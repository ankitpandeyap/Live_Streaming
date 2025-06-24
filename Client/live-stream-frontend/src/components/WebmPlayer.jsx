import React, { useEffect, useRef, useState } from 'react';

/**
 * A React component for playing recorded .webm videos directly.
 * This component expects a full, signed URL to the .webm file.
 *
 * @param {object} props - The component props.
 * @param {string} props.videoUrl - The full, signed URL of the .webm video to play.
 */
const WebmPlayer = ({ videoUrl }) => {
    const videoRef = useRef(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        const video = videoRef.current;
        if (!video) return;

        if (!videoUrl) {
            console.warn("WebmPlayer: videoUrl is undefined or empty, cannot load .webm source.");
            setError("No video URL provided for playback.");
            video.src = ''; // Clear source
            video.load();
            return;
        }

        setError(null); // Clear previous errors

        console.log("WebmPlayer: Attempting to load .webm video from URL:", videoUrl);
        video.src = videoUrl; // Set the video source to the provided URL
        video.load(); // Load the video

        const handleCanPlay = () => {
            console.log("WebmPlayer: Video is ready to play.");
            video.play().catch(playError => {
                // Autoplay might be prevented by browser policies, especially if not muted
                console.warn("WebmPlayer: Video playback was prevented (likely autoplay policy):", playError);
                setError("Playback started, but might be paused. Please click play.");
            });
        };

        const handleError = (e) => {
            console.error("WebmPlayer: Video element error:", e.target.error);
            let errorMessage = "An unknown video error occurred.";
            if (e.target.error) {
                switch (e.target.error.code) {
                    case e.target.error.MEDIA_ERR_ABORTED:
                        errorMessage = "Video playback aborted.";
                        break;
                    case e.target.error.MEDIA_ERR_NETWORK:
                        errorMessage = "A network error caused the video download to fail.";
                        break;
                    case e.target.error.MEDIA_ERR_DECODE:
                        errorMessage = "The video playback was aborted due to a corruption problem or because the video used features your browser does not support.";
                        break;
                    case e.target.error.MEDIA_ERR_SRC_NOT_SUPPORTED:
                        errorMessage = "The video could not be loaded, either because the server or network failed or because the format is not supported.";
                        break;
                    default:
                        errorMessage = "An unknown video error occurred.";
                        break;
                }
            }
            setError(errorMessage + " Please ensure the URL is valid and the file exists/is accessible.");
        };

        video.addEventListener('canplay', handleCanPlay);
        video.addEventListener('error', handleError); // Listen for errors from the video element itself

        // Cleanup function
        return () => {
            video.removeEventListener('canplay', handleCanPlay);
            video.removeEventListener('error', handleError);
            // Clear source when component unmounts or URL changes
            video.src = '';
            video.load();
        };
    }, [videoUrl]); // Re-run effect when videoUrl changes

    return (
        <div>
            {/* Display video player */}
            <video
                ref={videoRef}
                controls // Show controls (play/pause, seek, volume)
                muted // Mute by default for better autoplay chances
                autoPlay // Attempt to autoplay
                playsInline // Important for iOS inline playback
                style={{ width: '100%', maxWidth: '800px' }} // Basic styling
            >
                Your browser does not support the video tag.
            </video>
            {error && <p style={{ color: 'red', marginTop: '10px' }}>{error}</p>}
        </div>
    );
};

export default WebmPlayer;