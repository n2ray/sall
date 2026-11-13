import Foundation

// The fixture is generated locally from silence; it contains no captured Mac audio.
guard CommandLine.arguments.count == 2 else { fatalError("missing output path") }
var lines = [String]()
lines.append("runtimePackets=160")
for requested in [480, 512] {
    let encoder = try AudioEncoder(framesPerPacket: requested)
    guard encoder.framesPerPacket == requested, !encoder.cookie.isEmpty else {
        fatalError("AAC-ELD format unavailable")
    }
    lines.append("frames\(requested)=\(encoder.framesPerPacket)")
    lines.append("priming\(requested)=\(encoder.priming)")
    lines.append("cookie\(requested)=\(encoder.cookie.base64EncodedString())")
    for i in 0..<160 {
        guard let packet = encoder.encode([Float](repeating: 0, count: encoder.framesPerPacket * 2)), !packet.isEmpty else {
            fatalError("silence packet unavailable")
        }
        lines.append("packet\(requested).\(i)=\(packet.base64EncodedString())")
    }
}
try (lines.joined(separator: "\n") + "\n").write(toFile: CommandLine.arguments[1], atomically: true, encoding: .utf8)
