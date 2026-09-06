Add-Type -AssemblyName System.Speech
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$synth.SetOutputToWaveFile("scratch/sample_speech.wav")
$synth.Speak("What is Section 3(p) of the Patents Act?")
$synth.Dispose()
Write-Host "Generated sample_speech.wav successfully"
