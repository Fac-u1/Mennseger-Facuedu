import SwiftUI
import CryptoKit

struct EphemeralMessage: Identifiable {
    let id = UUID()
    let plaintext: String
    let ciphertextPreview: String
    let timestamp: Date
}

@MainActor
final class GuestVaultViewModel: ObservableObject {
    @Published var guestUid: String = ""
    @Published var displayName: String = ""
    @Published var fingerprint: String = ""
    @Published var publicKeyBase64: String = ""
    @Published var messages: [EphemeralMessage] = []

    private var privateKey: P256.KeyAgreement.PrivateKey?
    private var symmetricKey: SymmetricKey?

    init() {
        startNewGuestSession()
    }

    func startNewGuestSession() {
        let suffix = String(UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(8)).lowercased()
        guestUid = "guest_\(suffix)"
        displayName = "Invitado #\(suffix)"

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
        messages = []
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

    func wipeAll() {
        privateKey = nil
        symmetricKey = nil
        messages.removeAll()
        startNewGuestSession()
    }

    var shareUrl: String {
        let nameEncoded = displayName.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? displayName
        return "facuedumessenger://connect?uid=\(guestUid)&name=\(nameEncoded)&fp=\(fingerprint)"
    }
}

@main
struct FacueduMennsegerApp: App {
    @StateObject private var vm = GuestVaultViewModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView(vm: vm)
                .preferredColorScheme(.dark)
                .onChange(of: scenePhase) { newPhase in
                    if newPhase == .background {
                        vm.wipeAll()
                    }
                }
        }
    }
}

struct ContentView: View {
    @ObservedObject var vm: GuestVaultViewModel
    @State private var draft = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Modo Invitado Efímero · Se borra al cerrar")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(Color(red: 0.55, green: 0.46, blue: 1.0))
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

                        Button(role: .destructive) {
                            vm.wipeAll()
                        } label: {
                            Label("Borrar", systemImage: "trash.fill")
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
