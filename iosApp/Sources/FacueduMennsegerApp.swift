import SwiftUI
import CryptoKit
import Security
import AuthenticationServices

enum AuthMode {
    case unauthenticated
    case google
    case guest
}

struct EphemeralMessage: Identifiable {
    let id = UUID()
    let plaintext: String
    let ciphertextPreview: String
    let timestamp: Date
}

@MainActor
final class MessengerSessionViewModel: NSObject, ObservableObject, ASWebAuthenticationPresentationContextProviding {
    @Published var authMode: AuthMode = .unauthenticated
    @Published var uid: String = ""
    @Published var displayName: String = ""
    @Published var email: String = ""
    @Published var fingerprint: String = ""
    @Published var publicKeyBase64: String = ""
    @Published var messages: [EphemeralMessage] = []
    @Published var authError: String?
    @Published var isSigningIn: Bool = false
    @Published var googleClientId: String = ""

    private var privateKey: P256.KeyAgreement.PrivateKey?
    private var symmetricKey: SymmetricKey?
    private var activeWebSession: ASWebAuthenticationSession?
    private let clientIdDefaultsKey = "facuedu_google_ios_client_id"

    override init() {
        super.init()
        let plistId = Bundle.main.object(forInfoDictionaryKey: "GOOGLE_IOS_CLIENT_ID") as? String
        let savedId = UserDefaults.standard.string(forKey: clientIdDefaultsKey)
        googleClientId = (plistId?.isEmpty == false ? plistId : savedId) ?? ""
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first(where: { $0.isKeyWindow }) ?? ASPresentationAnchor()
    }

    private func initializeE2eeKeys() {
        let key = P256.KeyAgreement.PrivateKey()
        privateKey = key
        let rawPub = key.publicKey.x963Representation
        publicKeyBase64 = rawPub.base64EncodedString()

        let digest = SHA256.hash(data: rawPub)
        fingerprint = digest.prefix(8).map { String(format: "%02X", $0) }.joined(separator: ":")

        if let shared = try? key.sharedSecretFromKeyAgreement(with: key.publicKey) {
            symmetricKey = shared.hkdfDerivedSymmetricKey(
                using: SHA256.self,
                salt: Data("facuedu-e2ee".utf8),
                sharedInfo: Data(),
                outputByteCount: 32
            )
        }
    }

    func enterGuestMode() {
        authError = nil
        let suffix = String(UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(8)).lowercased()
        uid = "guest_\(suffix)"
        displayName = "Invitado #\(suffix)"
        email = ""
        messages = []
        initializeE2eeKeys()
        authMode = .guest
    }

    func signInWithGoogle(customClientId: String? = nil) {
        if let provided = customClientId?.trimmingCharacters(in: .whitespacesAndNewlines), !provided.isEmpty {
            googleClientId = provided
            UserDefaults.standard.set(provided, forKey: clientIdDefaultsKey)
        }

        let clientId = googleClientId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clientId.isEmpty else {
            authError = "Configurá tu Google iOS OAuth Client ID para continuar con Google."
            return
        }

        authError = nil
        isSigningIn = true

        let verifier = randomUrlSafeString(byteCount: 32)
        let challenge = base64Url(Data(SHA256.hash(data: Data(verifier.utf8))))
        let state = randomUrlSafeString(byteCount: 16)
        let redirectScheme = reversedClientIdScheme(clientId: clientId) ?? "facuedumessenger"
        let redirectUri = "\(redirectScheme):/oauth2redirect"

        var components = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
        components.queryItems = [
            URLQueryItem(name: "client_id", value: clientId),
            URLQueryItem(name: "redirect_uri", value: redirectUri),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: "openid email profile"),
            URLQueryItem(name: "code_challenge", value: challenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "state", value: state)
        ]

        guard let authUrl = components.url else {
            isSigningIn = false
            authError = "No se pudo construir la URL de Google OAuth."
            return
        }

        let session = ASWebAuthenticationSession(
            url: authUrl,
            callbackURLScheme: redirectScheme
        ) { [weak self] callbackUrl, error in
            Task { @MainActor in
                guard let self else { return }
                if let error {
                    self.isSigningIn = false
                    self.authError = error.localizedDescription
                    return
                }
                guard let callbackUrl,
                      let items = URLComponents(url: callbackUrl, resolvingAgainstBaseURL: false)?.queryItems,
                      items.first(where: { $0.name == "state" })?.value == state,
                      let code = items.first(where: { $0.name == "code" })?.value else {
                    self.isSigningIn = false
                    self.authError = "Respuesta OAuth de Google inválida."
                    return
                }
                await self.exchangeGoogleCode(
                    clientId: clientId,
                    code: code,
                    verifier: verifier,
                    redirectUri: redirectUri
                )
            }
        }
        session.presentationContextProvider = self
        session.prefersEphemeralWebBrowserSession = false
        activeWebSession = session
        if !session.start() {
            isSigningIn = false
            authError = "No se pudo iniciar la sesión web de Google."
        }
    }

    private func exchangeGoogleCode(
        clientId: String,
        code: String,
        verifier: String,
        redirectUri: String
    ) async {
        do {
            var tokenReq = URLRequest(url: URL(string: "https://oauth2.googleapis.com/token")!)
            tokenReq.httpMethod = "POST"
            tokenReq.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
            var bodyComp = URLComponents()
            bodyComp.queryItems = [
                URLQueryItem(name: "client_id", value: clientId),
                URLQueryItem(name: "code", value: code),
                URLQueryItem(name: "code_verifier", value: verifier),
                URLQueryItem(name: "grant_type", value: "authorization_code"),
                URLQueryItem(name: "redirect_uri", value: redirectUri)
            ]
            tokenReq.httpBody = bodyComp.percentEncodedQuery?.data(using: .utf8)

            let (tokenData, _) = try await URLSession.shared.data(for: tokenReq)
            let tokenJson = try JSONSerialization.jsonObject(with: tokenData) as? [String: Any]
            guard let accessToken = tokenJson?["access_token"] as? String else {
                throw NSError(domain: "GoogleAuth", code: -1, userInfo: [NSLocalizedDescriptionKey: "No se recibió token de acceso de Google."])
            }

            var profileReq = URLRequest(url: URL(string: "https://openidconnect.googleapis.com/v1/userinfo")!)
            profileReq.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
            let (profileData, _) = try await URLSession.shared.data(for: profileReq)
            let profile = try JSONSerialization.jsonObject(with: profileData) as? [String: Any]
            guard let sub = profile?["sub"] as? String else {
                throw NSError(domain: "GoogleAuth", code: -2, userInfo: [NSLocalizedDescriptionKey: "No se pudo leer el perfil de Google."])
            }

            uid = "google_\(sub)"
            displayName = (profile?["name"] as? String) ?? "Usuario Google"
            email = (profile?["email"] as? String) ?? ""
            messages = []
            initializeE2eeKeys()
            authMode = .google
            isSigningIn = false
        } catch {
            isSigningIn = false
            authError = error.localizedDescription
        }
    }

    func sendEncrypted(_ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let key = symmetricKey else { return }
        if let sealed = try? AES.GCM.seal(Data(trimmed.utf8), using: key),
           let combined = sealed.combined {
            let preview = String(combined.base64EncodedString().prefix(28)) + "..."
            messages.append(EphemeralMessage(plaintext: trimmed, ciphertextPreview: preview, timestamp: Date()))
        }
    }

    func signOutOrWipe() {
        privateKey = nil
        symmetricKey = nil
        messages.removeAll()
        uid = ""
        displayName = ""
        email = ""
        fingerprint = ""
        publicKeyBase64 = ""
        authMode = .unauthenticated
    }

    func wipeGuestOnBackground() {
        if authMode == .guest {
            signOutOrWipe()
        }
    }

    var shareUrl: String {
        let nameEncoded = displayName.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? displayName
        return "facuedumessenger://connect?uid=\(uid)&name=\(nameEncoded)&fp=\(fingerprint)"
    }

    private func reversedClientIdScheme(clientId: String) -> String? {
        let suffix = ".apps.googleusercontent.com"
        guard clientId.hasSuffix(suffix) else { return nil }
        let prefix = String(clientId.dropLast(suffix.count))
        return "com.googleusercontent.apps.\(prefix)"
    }

    private func randomUrlSafeString(byteCount: Int) -> String {
        var bytes = [UInt8](repeating: 0, count: byteCount)
        _ = SecRandomCopyBytes(kSecRandomDefault, byteCount, &bytes)
        return base64Url(Data(bytes))
    }

    private func base64Url(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}

@main
struct FacueduMennsegerApp: App {
    @StateObject private var vm = MessengerSessionViewModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            Group {
                if vm.authMode == .unauthenticated {
                    AuthLandingView(vm: vm)
                } else {
                    ContentView(vm: vm)
                }
            }
            .preferredColorScheme(.dark)
            .onChange(of: scenePhase) { newPhase in
                if newPhase == .background {
                    vm.wipeGuestOnBackground()
                }
            }
        }
    }
}

struct AuthLandingView: View {
    @ObservedObject var vm: MessengerSessionViewModel
    @State private var showClientIdConfig = false
    @State private var clientIdInput = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 18) {
                Spacer()

                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 54))
                    .foregroundColor(Color(red: 0.0, green: 0.87, blue: 0.71))

                Text("Facuedu.uy Mennseger")
                    .font(.title.bold())

                Text("Mensajería cifrada E2EE (ECDH P-256 + AES-256-GCM). Ingresá con tu cuenta de Google o probá en Modo Invitado.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)

                if let err = vm.authError {
                    Text(err)
                        .font(.caption)
                        .foregroundColor(.red)
                        .padding(10)
                        .frame(maxWidth: .infinity)
                        .background(Color.red.opacity(0.14))
                        .cornerRadius(12)
                        .padding(.horizontal)
                }

                VStack(spacing: 12) {
                    Button {
                        if vm.googleClientId.isEmpty {
                            showClientIdConfig = true
                        } else {
                            vm.signInWithGoogle()
                        }
                    } label: {
                        HStack {
                            if vm.isSigningIn {
                                ProgressView()
                            } else {
                                Text("G")
                                    .font(.headline.bold())
                                    .frame(width: 24, height: 24)
                                    .background(Color.white)
                                    .foregroundColor(.black)
                                    .clipShape(Circle())
                            }
                            Text(vm.isSigningIn ? "Conectando..." : "Continuar con Google")
                                .fontWeight(.semibold)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color(red: 0.0, green: 0.87, blue: 0.71))
                    .disabled(vm.isSigningIn)

                    Button {
                        vm.enterGuestMode()
                    } label: {
                        Label("Entrar en Modo Prueba (Invitado)", systemImage: "person.crop.circle")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                    }
                    .buttonStyle(.bordered)
                    .disabled(vm.isSigningIn)

                    Button("Configurar Google Client ID") {
                        clientIdInput = vm.googleClientId
                        showClientIdConfig.toggle()
                    }
                    .font(.caption)
                    .foregroundColor(.secondary)
                }
                .padding(.horizontal, 24)

                if showClientIdConfig {
                    VStack(spacing: 8) {
                        TextField("Google iOS Client ID (.apps.googleusercontent.com)", text: $clientIdInput)
                            .textFieldStyle(.roundedBorder)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                        Button("Guardar y conectar con Google") {
                            showClientIdConfig = false
                            vm.signInWithGoogle(customClientId: clientIdInput)
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .padding(.horizontal, 24)
                }

                Spacer()

                Text("En Modo Invitado la cuenta se borra automáticamente al salir o cerrar la app.")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
            }
            .padding()
            .background(Color(red: 0.04, green: 0.06, blue: 0.09).ignoresSafeArea())
        }
    }
}

struct ContentView: View {
    @ObservedObject var vm: MessengerSessionViewModel
    @State private var draft = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(
                        vm.authMode == .guest
                            ? "Modo Invitado Efímero · Se borra al cerrar"
                            : "Google · \(vm.email.isEmpty ? vm.displayName : vm.email)"
                    )
                    .font(.caption.weight(.semibold))
                    .foregroundColor(
                        vm.authMode == .guest
                            ? Color(red: 0.55, green: 0.46, blue: 1.0)
                            : Color(red: 0.0, green: 0.87, blue: 0.71)
                    )

                    Text("\(vm.displayName) · FP: \(vm.fingerprint)")
                        .font(.caption2.monospaced())
                        .foregroundColor(.secondary)

                    HStack(spacing: 10) {
                        ShareLink(item: vm.shareUrl) {
                            Label("Compartir clave E2EE", systemImage: "key.fill")
                                .font(.caption.weight(.semibold))
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(Color(red: 0.0, green: 0.87, blue: 0.71))

                        if vm.authMode == .guest {
                            Button("Google") {
                                vm.signOutOrWipe()
                                vm.signInWithGoogle()
                            }
                            .font(.caption.weight(.semibold))
                            .buttonStyle(.bordered)
                        }

                        Button(role: .destructive) {
                            vm.signOutOrWipe()
                        } label: {
                            Label(vm.authMode == .guest ? "Borrar" : "Salir", systemImage: "trash.fill")
                                .font(.caption.weight(.semibold))
                        }
                        .buttonStyle(.bordered)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(Color(red: 0.06, green: 0.10, blue: 0.14))
                .cornerRadius(16)
                .padding(.horizontal)

                List(vm.messages) { msg in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(msg.plaintext)
                            .font(.body)
                        Text("AES-256-GCM · \(msg.ciphertextPreview)")
                            .font(.caption2.monospaced())
                            .foregroundColor(.secondary)
                    }
                    .listRowBackground(Color(red: 0.09, green: 0.14, blue: 0.19))
                }
                .scrollContentBackground(.hidden)

                HStack(spacing: 10) {
                    TextField("Mensaje cifrado...", text: $draft)
                        .textFieldStyle(.roundedBorder)
                    Button("Enviar") {
                        vm.sendEncrypted(draft)
                        draft = ""
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color(red: 0.0, green: 0.87, blue: 0.71))
                }
                .padding()
            }
            .background(Color(red: 0.04, green: 0.06, blue: 0.09).ignoresSafeArea())
            .navigationTitle("Facuedu.uy Mennseger")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
