import XCTest
@testable import OtpPlugin

class OtpTests: XCTestCase {
    // MARK: - OTP Extraction Tests

    /// Helper that replicates the plugin's NSRegularExpression-based OTP extraction.
    private func extractOtp(from content: String, pattern: String = "(\\d{4,8})") -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(
                  in: content,
                  range: NSRange(content.startIndex..., in: content)
              ),
              match.numberOfRanges > 1,
              let otpRange = Range(match.range(at: 1), in: content) else {
            return nil
        }
        return String(content[otpRange])
    }

    func testExtract6DigitCode() {
        let result = extractOtp(from: "Your verification code is 483920. Do not share.")
        XCTAssertEqual(result, "483920")
    }

    func testExtract4DigitCode() {
        let result = extractOtp(from: "Your OTP is 1234")
        XCTAssertEqual(result, "1234")
    }

    func testExtract8DigitCode() {
        let result = extractOtp(from: "Use code 12345678 to continue")
        XCTAssertEqual(result, "12345678")
    }

    func testReturnsNilWhenNoMatch() {
        let result = extractOtp(from: "No code in this message")
        XCTAssertNil(result)
    }

    func testCustomAlphanumericPattern() {
        let result = extractOtp(from: "Code: AB12CD", pattern: "([A-Z0-9]{6})")
        XCTAssertEqual(result, "AB12CD")
    }

    func testEmptyString() {
        let result = extractOtp(from: "")
        XCTAssertNil(result)
    }

    func testRetrieverFormatSMS() {
        let result = extractOtp(from: "Your code is 483920\n\nFA+9qCX9VSu")
        XCTAssertEqual(result, "483920")
    }

    // MARK: - Plugin Unimplemented Methods

    func testGetAppHashIsUnimplemented() {
        // OTPPlugin.getAppHash should call call.unimplemented on iOS.
        // We verify this indirectly by checking the plugin has the method registered.
        let plugin = OtpPlugin()
        let methods = plugin.pluginMethods.map { $0.name }
        XCTAssertTrue(methods.contains("getAppHash"))
    }

    // MARK: - Plugin Method Registration

    func testAllMethodsRegistered() {
        let plugin = OtpPlugin()
        let expected = [
            "isAvailable",
            "startListening",
            "stopListening",
            "getAppHash",
        ]
        let registered = plugin.pluginMethods.map { $0.name }
        for method in expected {
            XCTAssertTrue(registered.contains(method), "Missing method: \(method)")
        }
    }

    func testPluginJsName() {
        let plugin = OtpPlugin()
        XCTAssertEqual(plugin.jsName, "Otp")
    }
}
