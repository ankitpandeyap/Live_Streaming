import React, { useRef, useState, useEffect } from "react";
import "./App.css"; // Aapki CSS file
import HLSPlayer from "./components/HLSPlayer";
import WebmPlayer from "./components/WebmPlayer"; // NEW: WebmPlayer import karein

function App() {
  const videoRef = useRef(null); // Local webcam preview ke liye ref
  const localStreamRef = useRef(null); // Actual MediaStream ke liye ref
  const mediaRecorderRef = useRef(null); // MediaRecorder ke liye ref
  const rawDataWebSocketRef = useRef(null); // Backend (Redis ingestion) ko raw media data bhejne ke liye WebSocket

  const [isStreamingLocal, setIsStreamingLocal] = useState(false); // Local webcam active ke liye state
  const [isBroadcasting, setIsBroadcasting] = useState(false); // Live broadcast active ke liye state
  const [error, setError] = useState(null); // Errors display karne ke liye state
  const [liveStreamId, setLiveStreamId] = useState(""); // Active live broadcast ke liye ID
  const [playingRecordedId, setPlayingRecordedId] = useState(null); // Recorded stream ke liye ID jo play ho rahi hai
  const [recordedStreamUrl, setRecordedStreamUrl] = useState(null); // Recorded .webm playback ke liye Signed URL

  // Aapke Spring Boot backend ke liye Base URL
  const API_BASE_URL = "http://localhost:8082";
  // Raw media data ingestion ke liye WebSocket URL (backend ko yeh endpoint chahiye)
  const RAW_DATA_WEBSOCKET_URL = `${API_BASE_URL.replace('http', 'ws')}/raw-media-ingest`;

  // Local webcam stream start karne ka function
  const startLocalStream = async () => {
    setError(null); // Pichhle errors clear karein
    try {
      // User media (video aur audio) request karein, specific constraints ke saath
      const mediaStream = await navigator.mediaDevices.getUserMedia({
        video: {
          width: { ideal: 1280 }, // Target 720p resolution
          height: { ideal: 720 },
          frameRate: { ideal: 24, min: 15, max: 30 } // Target 24 FPS
        },
        audio: true,
      });
      localStreamRef.current = mediaStream; // Stream store karein
      setIsStreamingLocal(true); // State update karein

      if (videoRef.current) {
        videoRef.current.srcObject = mediaStream; // Video element ko stream assign karein
        await videoRef.current
          .play() // Video play karne ki koshish karein
          .catch((e) => console.error("Error playing video:", e));
      }
    } catch (err) {
      console.error("Error accessing media devices:", err);
      setError("Error accessing webcam: " + err.name + " - " + err.message);
      setIsStreamingLocal(false);
      localStreamRef.current = null;
    }
  };

  // Local webcam stream stop karne ka function
  const stopLocalStream = () => {
    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((track) => track.stop()); // Sabhi tracks stop karein
      localStreamRef.current = null;
    }
    setIsStreamingLocal(false);
    if (videoRef.current) {
      videoRef.current.srcObject = null; // Video element source clear karein
    }
  };

  // Live stream broadcast start karne ka function
  const startBroadcast = async () => {
    if (!localStreamRef.current) {
      await startLocalStream(); // Ensure karein ki broadcast se pehle local stream active hai
      if (!localStreamRef.current) {
        setError("Failed to start local stream for broadcast.");
        return;
      }
    }

    if (isBroadcasting) {
      console.log("Already broadcasting.");
      return;
    }

    setError(null);
    try {
      // 1. Is broadcast session ke liye ek unique stream ID generate karein
      const id = `stream-${Date.now()}`; // Example: "stream-1701234567890"
      setLiveStreamId(id); // Live stream ID set karein
      console.log(`Attempting to start broadcast with ID: ${id}`);

      // 2. Backend ko raw data ingestion ke liye WebSocket initialize karein
      // The stream ID is appended to the URL for the backend to identify the stream/channel
      rawDataWebSocketRef.current = new WebSocket(`${RAW_DATA_WEBSOCKET_URL}/${id}`);

      rawDataWebSocketRef.current.onopen = () => {
        console.log("Raw data WebSocket connected for stream:", id);
        // 3. WebSocket open hone ke baad MediaRecorder initialize karein
        try {
          const options = {
            mimeType: "video/webm; codecs=vp8", // Broad compatibility ke liye VP8 use karein
            timeslice: 500, // Har 500ms mein data chunks bhejein
            videoBitsPerSecond: 1000000 // Target video bitrate (1 Mbps)
          };

          // MIME type support check karein aur zaroorat padne par fallback karein
          if (!MediaRecorder.isTypeSupported(options.mimeType)) {
            console.warn(`${options.mimeType} is not supported, trying fallback...`);
            options.mimeType = "video/webm"; // Generic webm par fallback karein
            if (!MediaRecorder.isTypeSupported(options.mimeType)) {
              console.error("No supported MIME type for MediaRecorder found.");
              setError("Your browser does not support required video recording formats.");
              stopBroadcast();
              return;
            }
          }

          mediaRecorderRef.current = new MediaRecorder(
            localStreamRef.current,
            options
          );

          mediaRecorderRef.current.ondataavailable = (event) => {
            if (
              event.data.size > 0 &&
              rawDataWebSocketRef.current &&
              rawDataWebSocketRef.current.readyState === WebSocket.OPEN
            ) {
              // Redis ko publish karne ke liye Blob data WebSocket par bhejein
              rawDataWebSocketRef.current.send(event.data);
            }
          };

          mediaRecorderRef.current.onstop = () => {
            console.log("MediaRecorder stopped.");
            // WebSocket ko stopBroadcast handle karega
          };

          mediaRecorderRef.current.onerror = (event) => {
            console.error("MediaRecorder error:", event.error);
            setError(`MediaRecorder error: ${event.error.name} - ${event.error.message}`);
            stopBroadcast();
          };

          mediaRecorderRef.current.start(options.timeslice); // Timeslice ke saath recording start karein
          setIsBroadcasting(true); // Broadcasting state update karein
          console.log("MediaRecorder started, sending data to Raw Data WebSocket.");
        } catch (mrError) {
          console.error("Error setting up MediaRecorder:", mrError);
          setError("Error setting up MediaRecorder: " + mrError.message);
          stopBroadcast(); // Agar MediaRecorder fail hota hai toh clean up karein
        }
      };

      rawDataWebSocketRef.current.onmessage = (event) => {
        // Yeh WebSocket bhejne ke liye hai, receive karne ke liye nahi, lekin debugging ke liye achha hai.
        console.log("Raw data WebSocket message received (should not happen often):", event.data);
      };

      rawDataWebSocketRef.current.onclose = (event) => {
        console.log("Raw data WebSocket closed:", event);
        if (!event.wasClean) {
          setError(`Raw data WebSocket closed unexpectedly. Code: ${event.code}, Reason: ${event.reason}`);
        } else {
          setError(null); // Agar clean close hai toh error clear karein
        }
        setIsBroadcasting(false); // Broadcast status update karein
        mediaRecorderRef.current = null; // MediaRecorder ref clear karein
        rawDataWebSocketRef.current = null; // rawDataWebSocketRef ref clear karein
        setLiveStreamId(""); // Close par streamId clear karein
      };

      rawDataWebSocketRef.current.onerror = (err) => {
        console.error("Raw data WebSocket error:", err);
        setError("Raw data WebSocket error: Could not connect to ingestion server.");
        stopBroadcast(); // Error hone par sab kuch stop karne ki koshish karein
      };
    } catch (wsError) {
      console.error("Error setting up Raw data WebSocket:", wsError);
      setError("Error setting up Raw data WebSocket: " + wsError.message);
      setIsBroadcasting(false);
      setLiveStreamId("");
    }
  };

  // Broadcast stop karne ka function
  const stopBroadcast = () => {
    if (
      mediaRecorderRef.current &&
      mediaRecorderRef.current.state !== "inactive"
    ) {
      mediaRecorderRef.current.stop(); // Yeh onstop event trigger karega
    }
    if (
      rawDataWebSocketRef.current &&
      rawDataWebSocketRef.current.readyState === WebSocket.OPEN
    ) {
      rawDataWebSocketRef.current.close(1000, "User stopped broadcast"); // Clean close
    }
    setIsBroadcasting(false);
    setLiveStreamId("");
  };

  // Recorded stream (WebM) fetch aur play karne ka function
  // In a real app, 'recordId' would come from a list of user's recordings
  const playRecordedStream = async (recordId) => {
    // Koi bhi active live stream ya pichhla recorded stream state clear karein
    stopBroadcast();
    setLiveStreamId("");
    setPlayingRecordedId(null);
    setRecordedStreamUrl(null);
    setError(null);

    // Assuming you have a JWT token for authentication (e.g., from login)
    // Replace with your actual token retrieval logic
    const authToken = localStorage.getItem('jwtToken'); // Or from Redux/Context

    if (!authToken) {
      setError("Authentication token missing. Cannot play recorded stream.");
      return;
    }

    try {
      console.log(`Requesting signed URL for recorded stream ID: ${recordId}`);
      const response = await fetch(`${API_BASE_URL}/api/recorded-streams/${recordId}/stream-url`, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${authToken}`,
          'Content-Type': 'application/json'
        }
      });

      if (!response.ok) {
        if (response.status === 404) {
          throw new Error("Recorded stream not found or not ready.");
        } else if (response.status === 403) {
          throw new Error("Access denied to this recorded stream.");
        }
        throw new Error(`Failed to get signed URL: ${response.statusText}`);
      }

      const signedUrl = await response.text(); // Backend URL ko plain text ke roop mein return karta hai
      console.log("Received signed URL:", signedUrl);

      setPlayingRecordedId(recordId); // Recorded stream ki ID set karein jo play ho rahi hai
      // API_BASE_URL prepend karein kyunki backend se signedUrl relative hai (/api/recorded-streams/...)
      setRecordedStreamUrl(`${API_BASE_URL}${signedUrl}`);

    } catch (err) {
      console.error("Error fetching recorded stream URL:", err);
      setError("Error playing recorded stream: " + err.message);
      setPlayingRecordedId(null);
      setRecordedStreamUrl(null);
    }
  };

  // Component unmount hone par local stream aur websockets ke liye cleanup effect
  useEffect(() => {
    return () => {
      stopBroadcast(); // Ensure karein ki broadcast stop ho gayi hai
      stopLocalStream(); // Ensure karein ki local stream stop ho gayi hai
    };
  }, []); // Empty dependency array ka matlab hai ki yeh sirf mount aur unmount par run hota hai

  return (
    <div className="App">
      <header className="App-header">
        <h1>Live Stream Frontend</h1>
      </header>
      <main>
        <div className="video-container">
          {/* Local webcam preview display karein */}
          <div className="local-video-wrapper">
            <h2>Your Local Stream</h2>
            <video
              ref={videoRef}
              autoPlay
              muted
              playsInline
              className="local-video"
            ></video>
          </div>

          {error && <p className="error-message">{error}</p>}

          {/* LIVE stream ke liye HLS Player display karein */}
          {isBroadcasting && liveStreamId && (
            <div className="hls-player-wrapper">
              <h2>Live HLS Stream (ID: {liveStreamId})</h2>
              {/* HLSPlayer ab SIRF live HLS handle karta hai */}
              <HLSPlayer streamId={liveStreamId} />
            </div>
          )}

          {/* RECORDED stream ke liye WebmPlayer display karein */}
          {playingRecordedId && recordedStreamUrl && (
            <div className="webm-player-wrapper">
              <h2>Recorded Stream Playback (ID: {playingRecordedId})</h2>
              {/* WebmPlayer signed URL use karke seedhe .webm playback handle karta hai */}
              <WebmPlayer videoUrl={recordedStreamUrl} />
            </div>
          )}
        </div>

        <div className="controls">
          {!isBroadcasting ? (
            <>
              {!isStreamingLocal && (
                <button onClick={startLocalStream} disabled={isStreamingLocal}>
                  Start Local Webcam
                </button>
              )}
              {isStreamingLocal && (
                <button
                  onClick={startBroadcast}
                  disabled={!localStreamRef.current || isBroadcasting}
                >
                  Start Broadcast
                </button>
              )}
            </>
          ) : (
            <button onClick={stopBroadcast}>Stop Broadcast</button>
          )}
          {isStreamingLocal && !isBroadcasting && (
            <button onClick={stopLocalStream}>Stop Local Webcam</button>
          )}

          {/* Recorded stream play karne ke liye button (example ID) */}
          {/* In a real app, you'd fetch a list of recordings and let the user pick one */}
          <button
            onClick={() => playRecordedStream(1)} // Apne DB se actual record ID use karein
            disabled={playingRecordedId === 1}
          >
            Play Recorded Stream (ID 1)
          </button>
          {playingRecordedId && (
            <button onClick={() => { setPlayingRecordedId(null); setRecordedStreamUrl(null); }}>
              Stop Recorded Playback
            </button>
          )}
        </div>
        <p className="status">
          Status:{" "}
          {isBroadcasting
            ? `Broadcasting (ID: ${liveStreamId})`
            : isStreamingLocal
              ? "Local Webcam Active"
              : "Not Streaming"}
          {playingRecordedId &&
            ` | Playing Recording (ID: ${playingRecordedId})`}
        </p>
      </main>
    </div>
  );
}

export default App;