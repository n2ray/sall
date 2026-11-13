import Foundation

// Same command stream as AudioPlayoutTrace.java. The production Swift model is compiled unchanged.
var m = AudioPlayout()
func n(_ v: Double?) -> String { v.map { String($0) } ?? "null" }
func b(_ v: Bool) -> String { v ? "true" : "false" }
func output(_ o: AudioPlayout.Output) -> String {
    var p = [String(o.schedules.count)]
    for s in o.schedules {
        p += [String(s.id), s.part == .body ? "BODY" : "TAIL", String(s.offset), String(s.count), n(s.at),
              n(s.start), String(s.frames), String(s.change), b(s.fadeIn), b(s.fadeOut), n(s.stamp),
              String(s.seq), String(s.epoch), n(s.decision.due), n(s.decision.ear), n(s.decision.picture), n(s.goesOut)]
    }
    p += [String(o.discards.count)] + o.discards.map { String($0) }
    return p.joined(separator: " ")
}
while let line = readLine() {
    let p = line.split(separator: " ").map(String.init)
    func d(_ i: Int) -> Double { Double(p[i])! }
    func i(_ k: Int) -> Int { Int(p[k])! }
    var result = "ok"
    switch p[0] {
    case "change":
        var left = p[2].split(separator: ",").map { Float($0)! }
        var right = p[3].split(separator: ",").map { Float($0)! }
        AudioPlayout.change(&left, &right, by: i(1))
        result = ([String(left.count)] + left.map { String(Double($0)) } + [String(right.count)] + right.map { String(Double($0)) }).joined(separator: " ")
    case "fade":
        var x = p[3].split(separator: ",").map { Float($0)! }
        AudioPlayout.fade(&x, in: i(1) != 0, frames: i(2))
        result = x.map { String(Double($0)) }.joined(separator: " ")
    case "new": m = AudioPlayout()
    case "format": result = b(m.format(epoch: i(1), framesPerPacket: i(2), primingFrames: i(3), cookie: Data([UInt8(i(4))])))
    case "frame": m.frame(stamp: d(1), arrival: d(2))
    case "latency": m.latency(output: d(1), io: d(2), refresh: d(3), streamFPS: d(4))
    case "rtt": m.rtt(median: p[1] == "null" ? nil : d(1))
    case "away": m.away(i(1) != 0)
    case "mute": m.mute(i(1) != 0)
    case "played": result = output(m.played(sample: d(1), at: d(2), now: d(3)))
    case "packet":
        let a = m.packet(epoch: i(1), seq: UInt32(p[2])!, stamp: d(3), segmentStart: i(4) != 0, arrival: d(5), now: d(6))
        result = [b(a.decode), b(a.resetFirst), String(a.dropFront), a.id.map { String($0) } ?? "null", output(a.output)].joined(separator: " ")
    case "failed": m.decodeFailed()
    case "tick": result = output(m.tick(now: d(1)))
    case "reset": result = output(m.reset(p[1] == "SESSION" ? .session : p[1] == "PLAYBACK" ? .playback : .jump))
    case "stats":
        let s = m.takeSecond(now: d(1))
        result = [s.behindMs.map { String($0) } ?? "null", String(s.placed), String(s.late), String(s.duplicates), String(s.framesAdded), String(s.framesDropped), String(s.jumps), String(s.joins), String(s.jumpDrops), n(s.need), n(s.cover), n(s.delay), n(s.lag), n(s.error)].joined(separator: " ")
    case "jump":
        if let j = m.takeJump() {
            let cause: String
            switch j.cause { case .step: cause = "STEP"; case .error: cause = "ERROR"; case .ahead: cause = "AHEAD"; case .route: cause = "ROUTE" }
            result = n(j.by) + " " + cause
        } else { result = "null" }
    case "read": result = [n(m.floor), n(m.cover(now: d(1))), n(m.need(now: d(1))), n(m.pictureLag(now: d(1))), n(m.deadline), b(m.holding)].joined(separator: " ")
    default: fatalError("unknown command")
    }
    print(result)
}
