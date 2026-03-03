import Foundation
import Capacitor
import UIKit

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(OtpPlugin)
public class OtpPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "OtpPlugin"
    public let jsName = "Otp"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "isAvailable", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startListening", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopListening", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getAppHash", returnType: CAPPluginReturnPromise),
    ]
    
    // MARK: - Private State

    private var clipboardTimer: Timer?
    private var lastChangeCount: Int = 0
    private var otpPattern: String = "(\\d{4,8})"
    
    /// Whether clipboard monitoring is allowed on this iOS version by default.
    /// iOS 16+ triggers a paste banner on every clipboard read.
    private var isClipboardAllowedByDefault: Bool {
        if #available(iOS 16, *) { return false }
        return true
    }
    
    // MARK: - isAvailable

    @objc func isAvailable(_ call: CAPPluginCall) {
        let explicitEnable = call.getBool("enableClipboardFallback")
            ?? getConfig().getBoolean("enableClipboardFallback", isClipboardAllowedByDefault)
        let canUseClipboard = explicitEnable

        if canUseClipboard {
            call.resolve([
                "available": true,
                "method": "clipboard"
            ])
        } else {
            call.resolve([
                "available": false,
                "reason": "ios16+_paste_banner"
            ])
        }
    }
    
    // MARK: - startListening

    @objc func startListening(_ call: CAPPluginCall) {
        // Override OTP pattern if provided
        if let pattern = call.getString("otpPattern") ?? getConfig().getString("otpPattern") {
            otpPattern = pattern
        }

        // Determine whether clipboard monitoring is allowed
        let explicitEnable = call.getBool("enableClipboardFallback")
            ?? getConfig().getBoolean("enableClipboardFallback", isClipboardAllowedByDefault)
        let canUseClipboard = explicitEnable

        guard canUseClipboard else {
            // Resolve immediately — iOS 16+ should use autocomplete="one-time-code"
            call.resolve([
                "warning": "Clipboard monitoring disabled on iOS 16+. Use autocomplete='one-time-code' on your input field."
            ])
            return
        }

        // Capture the initial clipboard state to avoid false positives
        lastChangeCount = UIPasteboard.general.changeCount

        // Read polling interval from config (default 1.5 seconds)
        let interval: TimeInterval = {
            if let val = getConfig().getConfigJSON()["clipboardPollingInterval"] as? Double {
                return val
            }
            return 1.5
        }()

        // Start monitoring clipboard on the main thread
        DispatchQueue.main.async { [weak self] in
            self?.clipboardTimer?.invalidate()
            self?.clipboardTimer = Timer.scheduledTimer(
                withTimeInterval: interval,
                repeats: true
            ) { [weak self] _ in
                self?.checkClipboard()
            }
        }

        call.resolve()
    }

    // MARK: - stopListening

    @objc func stopListening(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.clipboardTimer?.invalidate()
            self?.clipboardTimer = nil
        }
        call.resolve()
    }
    
    // MARK: - Android-only (unimplemented)

    @objc func getAppHash(_ call: CAPPluginCall) {
        call.unimplemented("App hash is an Android-only feature.")
    }
    
    // MARK: - Clipboard Monitoring

    private func checkClipboard() {
        let currentCount = UIPasteboard.general.changeCount
        guard currentCount != lastChangeCount else { return }
        lastChangeCount = currentCount

        guard let content = UIPasteboard.general.string else { return }

        // Try to extract an OTP-like code
        guard let regex = try? NSRegularExpression(pattern: otpPattern),
              let match = regex.firstMatch(
                  in: content,
                  range: NSRange(content.startIndex..., in: content)
              ),
              match.numberOfRanges > 1,
              let otpRange = Range(match.range(at: 1), in: content)
        else {
            return
        }

        let otp = String(content[otpRange])
        notifyListeners("otpReceived", data: [
            "message": content,
            "otp": otp
        ])
    }

    // MARK: - Cleanup

    deinit {
        clipboardTimer?.invalidate()
    }
}
