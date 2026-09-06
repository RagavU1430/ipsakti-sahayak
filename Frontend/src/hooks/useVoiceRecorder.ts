import { useCallback, useEffect, useRef, useState } from 'react';

const MIME_CANDIDATES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4'];

export interface RecordingResult { blob: Blob; durationMs: number; previewUrl: string; }

export function useVoiceRecorder(onRecorded: (result: RecordingResult) => void) {
  const [isRecording, setIsRecording] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const recorder = useRef<MediaRecorder | null>(null);
  const stream = useRef<MediaStream | null>(null);
  const chunks = useRef<Blob[]>([]);
  const startedAt = useRef(0);
  const callback = useRef(onRecorded);
  const discard = useRef(false);
  useEffect(() => { callback.current = onRecorded; }, [onRecorded]);

  const stopTracks = useCallback(() => {
    stream.current?.getTracks().forEach((track) => track.stop());
    stream.current = null;
  }, []);

  const stop = useCallback((shouldDiscard = false) => {
    discard.current = shouldDiscard;
    if (recorder.current?.state === 'recording') recorder.current.stop();
    else stopTracks();
    setIsRecording(false);
  }, [stopTracks]);

  const start = useCallback(async () => {
    if (recorder.current?.state === 'recording') return;
    setError(null);
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      setError('MICROPHONE_UNAVAILABLE');
      return;
    }
    try {
      stream.current = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = MIME_CANDIDATES.find((value) => MediaRecorder.isTypeSupported(value));
      const current = mimeType ? new MediaRecorder(stream.current, { mimeType }) : new MediaRecorder(stream.current);
      chunks.current = [];
      discard.current = false;
      current.ondataavailable = (event) => { if (event.data.size > 0) chunks.current.push(event.data); };
      current.onerror = () => { setError('RECORDING_FAILED'); stopTracks(); setIsRecording(false); };
      current.onstop = () => {
        stopTracks();
        const durationMs = Math.max(0, Math.round(performance.now() - startedAt.current));
        const actualType = current.mimeType || mimeType || chunks.current[0]?.type || 'application/octet-stream';
        const blob = new Blob(chunks.current, { type: actualType });
        console.info('VOICE_CAPTURE', { mimeType: blob.type, sizeBytes: blob.size, durationMs });
        if (!discard.current && blob.size > 0) callback.current({ blob, durationMs, previewUrl: URL.createObjectURL(blob) });
        else if (!discard.current) setError('AUDIO_EMPTY');
      };
      recorder.current = current;
      current.start(250);
      startedAt.current = performance.now();
      setIsRecording(true);
    } catch (cause) {
      stopTracks();
      setIsRecording(false);
      setError(cause instanceof DOMException && cause.name === 'NotAllowedError' ? 'MICROPHONE_PERMISSION_DENIED' : 'MICROPHONE_UNAVAILABLE');
    }
  }, [stopTracks]);

  useEffect(() => () => stop(true), [stop]);
  return { isRecording, error, clearError: () => setError(null), start, stop };
}
