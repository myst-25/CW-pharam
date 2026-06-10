# CW Pharmacy PDF Saver

> *"Knowledge must be free, accessible, and shareable for all."*

An Xposed/LSPosed module designed to enhance the **CW Pharmacy** educational app. 

### What is CW Pharmacy?
CW Pharmacy is an educational application (built on the Appx white-label platform) designed for pharmacy students. It provides video courses, live streams, test series, and PDF study materials.

### What does this Module do?
This module utilizes the modern `libxposed` API (API 101) to hook into the CW Pharmacy app and provide the following feature:

1. **PDF Downloader & Decryptor**
   - The app natively restricts downloading or sharing study materials and scrambles/encrypts PDFs so they cannot be opened by standard viewers.
   - This module injects a custom Material Design Download button (a floating blue "DL" button) directly into the app's built-in PDF viewer.
   - When tapped, it automatically fetches the PDF, strips the XOR header scrambling or decrypts the AES-CBC encryption, and saves a clean, readable PDF directly to your device's `Downloads` folder.

### Requirements
- A rooted Android device.
- A modern Xposed framework manager (like **LSPosed**) that supports **libxposed API 101**.

### Community & Support
If you have questions, feedback, or just want to discuss the module:
*   **Developer:** [myst-25](https://github.com/myst-25)
*   **Telegram Profile:** [@Myst_25](https://t.me/Myst_25)
*   **Telegram Group Chat:** [Join the Group!](https://t.me/myst2123)

❤️ 🇵🇸 **Free Palestine**

---
*Disclaimer: This module is for educational and research purposes only.*
