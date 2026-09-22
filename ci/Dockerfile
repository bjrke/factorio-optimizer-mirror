FROM gradle:9.7.1-jdk25

# Google Chrome for the Karma-based jsBrowserTest (ChromeHeadless launcher).
# The deb pulls in all of Chrome's runtime dependencies via apt.
RUN wget -q -O /tmp/google-chrome.deb https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
    && apt-get update \
    && apt-get install -y --no-install-recommends /tmp/google-chrome.deb \
    && rm -f /tmp/google-chrome.deb \
    && rm -rf /var/lib/apt/lists/*

# karma-chrome-launcher looks up "google-chrome" (or CHROME_BIN) and does not
# pass --no-sandbox, which is required when Chrome runs as root in a container.
# This wrapper injects it.
RUN printf '#!/bin/sh\nexec /usr/bin/google-chrome --no-sandbox "$@"\n' > /usr/local/bin/google-chrome \
    && chmod +x /usr/local/bin/google-chrome

ENV CHROME_BIN=/usr/local/bin/google-chrome
