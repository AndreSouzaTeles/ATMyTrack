import Foundation

struct Stem: Codable, Identifiable, Equatable {
    var id = UUID().uuidString
    var name: String
    var file: String
    var frames: Int64
    var volume: Float = 1
    var pan: Float = 0
    var mute = false
    var solo = false
    var bus = ""
    var route = -1
    var peaks: [Float] = []
}
struct Marker: Codable, Identifiable, Equatable {
    var id = UUID().uuidString
    var name: String
    var start: Double
    var end: Double
}
struct DCA: Codable, Identifiable, Equatable {
    var id = UUID().uuidString
    var name: String
    var members: Set<String> = []
    var volume: Float = 1
    var mute = false
}
struct Bus: Codable, Identifiable, Equatable {
    var id = UUID().uuidString
    var name: String
    var volume: Float = 1
    var mute = false
    var route = -1
}
struct Song: Codable, Identifiable, Equatable {
    var id = UUID().uuidString
    var name: String
    var stems: [Stem] = []
    var key = "—"
    var artwork = ""
    var bpm = 70.0
    var beats = 4
    var denominator = 4
    var multiplier = 1.0
    var click = false
    var clickVolume: Float = 0.35
    var accent = true
    var clickSound = 0
    var clickRoute = -1
    var beatOffset = 0.0
    var smartClick = false
    var detectedBpm = 0.0
    var master: Float = 1
    var masterMute = false
    var markers: [Marker] = []
    var selectedMarker = ""
    var loop = false
    var dcas: [DCA] = []
    var buses: [Bus] = []
    var semitones = 0
    var pitchTracks: Set<String> = []
    var speed = 100
    var speedTracks: Set<String>? = nil
    var preset = 100
    var speedSelection: Set<String> { speedTracks ?? Set(stems.map(\.id)) }
    var globalSpeed: Bool { !stems.isEmpty && stems.allSatisfy { speedSelection.contains($0.id) } }
    var scale: Double { globalSpeed ? Double(speed)/100 : 1 }
    var effectiveBPM: Double { bpm * scale }
    var originalSeconds: Double { Double(stems.map(\.frames).max() ?? 0)/48000 }
    var seconds: Double { stems.map { Double($0.frames)/48000/rate(for: $0) }.max() ?? 0 }
    func rate(for stem: Stem) -> Double { speedSelection.contains(stem.id) ? Double(speed)/100 : 1 }
    func pitch(for stem: Stem) -> Int { pitchTracks.contains(stem.id) ? semitones : 0 }
}
enum Music {
    static let notes = ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"]
    static func frequency(_ midi: Int, a4: Double = 440) -> Double { a4 * pow(2, Double(midi-69)/12) }
    static func nearest(_ hz: Double, a4: Double = 440) -> Int { Int((69+12*log2(max(hz,1)/a4)).rounded()) }
    static func cents(_ hz: Double, midi: Int, a4: Double = 440) -> Double { 1200*log2(max(hz,1)/frequency(midi,a4:a4)) }
    static func note(_ midi: Int) -> String { notes[(midi%12+12)%12] + String(Int(floor(Double(midi)/12))-1) }
    static func time(_ seconds: Double) -> String { let s = max(0,Int(seconds)); return String(format:"%02d:%02d",s/60,s%60) }
    static func db(_ gain: Float) -> Double { gain > 0 ? max(-80,20*log10(Double(gain))) : -80 }
    static func gain(_ db: Double) -> Float { db <= -79.9 ? 0 : Float(pow(10,db/20)) }
}
struct Tuning: Identifiable {
    var id: String
    var notes: [Int]
}
struct Instrument: Identifiable {
    var id: String
    var tunings: [Tuning]
    static let guitar = [Tuning(id:"Standard",notes:[40,45,50,55,59,64]),Tuning(id:"Drop D",notes:[38,45,50,55,59,64]),Tuning(id:"D Standard",notes:[38,43,48,53,57,62]),Tuning(id:"Drop C",notes:[36,43,48,53,57,62]),Tuning(id:"Half Step Down",notes:[39,44,49,54,58,63])]
    static let all: [Instrument] = [
        .init(id:"Cromático",tunings:[]),.init(id:"Violão",tunings:guitar),.init(id:"Guitarra",tunings:guitar),
        .init(id:"Baixo 4 cordas",tunings:[.init(id:"Standard",notes:[28,33,38,43])]),
        .init(id:"Baixo 5 cordas",tunings:[.init(id:"Standard",notes:[23,28,33,38,43])]),
        .init(id:"Baixo 6 cordas",tunings:[.init(id:"Standard",notes:[23,28,33,38,43,48])]),
        .init(id:"Ukulele",tunings:[.init(id:"High G",notes:[67,60,64,69])]),
        .init(id:"Violino",tunings:[.init(id:"Standard",notes:[55,62,69,76])]),
        .init(id:"Viola",tunings:[.init(id:"Standard",notes:[48,55,62,69])]),
        .init(id:"Violoncelo",tunings:[.init(id:"Standard",notes:[36,43,50,57])]),
        .init(id:"Contrabaixo acústico",tunings:[.init(id:"Standard",notes:[28,33,38,43])]),
        .init(id:"Cavaquinho",tunings:[.init(id:"DGBD",notes:[62,67,71,74])]),
        .init(id:"Bandolim",tunings:[.init(id:"Standard",notes:[55,62,69,76])])]
}
