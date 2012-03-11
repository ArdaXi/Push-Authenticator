# Push Authenticator

A two-factor authentication solution for Android implementing the OCRA scheme (RFC 6287) and based on Google Authenticator.

************************
This application is not yet usable. I am putting the code online for feedback and accountability. Use at your own risk.
************************

## Why Push Authenticator?

The advantages of Push Authenticator over existing solutions like Google Authenticator are many. These include:

- Challenge-response. You don't need to wait 30 seconds for a new code, and a code can't be re-used, ever.

- Server authentication. You can verify the server, preventing MITM attacks.

- Automation. Instead of manually entering keys back and forth, the client will negotiate the credentials over the internet if a connection is available.

- Encryption. (WIP) Your secret keys are encrypted with a PIN, preventing mis-use if someone takes your phone.

- Key negotiation. (WIP) Rather than putting the key in a QR code on the user's computer, defeating the purpose of two-factor auth altogether, the keys are negotiated using Diffie-Hellman.

## Licence

Because I'm still figuring out what licencing to use, and this code is not for general use, for now I am maintaining full copyright on it. This **will** change in the future.

    © Ariën Holthuizen 2012