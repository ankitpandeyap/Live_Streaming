# streaming-backend/Dockerfile
# Use a lightweight base image with FFmpeg already installed
# This uses the official ffmpeg-full image, which includes all necessary libraries.
FROM jrottenberg/ffmpeg:4.1-ubuntu

# Set a working directory inside the container (optional but good practice)
WORKDIR /app

# Expose a port if FFmpeg were to serve content directly,
# but in our case, Spring Boot will serve the HLS, and FFmpeg will write to a volume.
# This line is primarily for documentation/understanding, not strictly required for current setup.
# EXPOSE 8080

# You can add any additional setup here if needed,
# but the base image is usually sufficient for most FFmpeg tasks.

# Set the default command to just keep the container running
# We will override this when we run FFmpeg from Spring Boot
CMD ["tail", "-f", "/dev/null"]
