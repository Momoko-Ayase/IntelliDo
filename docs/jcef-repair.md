# Repair instructions when JCEF cannot start

IntelliDo depends on JCEF for sign-in, community transport, trusted in-app browsing, and identity-security pages. A JCEF failure does not fall back to a JVM HTTP client or a system-browser session.

## Check these first

1. Start IntelliDo with the bundled JetBrains Runtime. Do not replace it with a JDK that omits JCEF.
2. On Windows, confirm GPU drivers work and the app is allowed to use the GPU.
3. Temporarily disable security software that injects into Chromium processes, then retry.
4. Confirm the diagnostic Java vendor and version come from the bundled runtime, not a system JDK.
5. Retry initialization. If it still fails, copy diagnostics and open a GitHub issue.

Do not import cookies from the system browser, and do not switch to another HTTP client.
