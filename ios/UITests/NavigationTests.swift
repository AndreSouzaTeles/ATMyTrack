import XCTest
final class NavigationTests:XCTestCase {
    func testMicrophonePermissionAndLifecycle() throws {
        let app=XCUIApplication()
        XCUIDevice.shared.orientation = .portrait
        app.resetAuthorizationStatus(for:.microphone);app.launch()
        openTuner(app)
        let request=app.buttons["PERMITIR / INICIAR"]
        for _ in 0..<4 {if request.isHittable{break};app.swipeUp()}
        XCTAssertTrue(request.isHittable);request.tap()
        let system=XCUIApplication(bundleIdentifier:"com.apple.springboard")
        let deny=system.alerts.buttons.matching(NSPredicate(format:"label CONTAINS[c] %@","Allow")).firstMatch
        XCTAssertTrue(deny.waitForExistence(timeout:10))
        let dontAllow=system.alerts.buttons["Don’t Allow"].exists ? system.alerts.buttons["Don’t Allow"]:system.alerts.buttons["Don't Allow"]
        dontAllow.tap()
        XCTAssertTrue(app.buttons["ABRIR AJUSTES DO MICROFONE"].waitForExistence(timeout:5))
        app.terminate();app.resetAuthorizationStatus(for:.microphone);app.launch();openTuner(app)
        for _ in 0..<4 {if request.isHittable{break};app.swipeUp()}
        request.tap()
        let allow=system.alerts.buttons["Allow"]
        XCTAssertTrue(allow.waitForExistence(timeout:10));allow.tap()
        XCTAssertTrue(app.staticTexts["ATIVO"].waitForExistence(timeout:10))
        XCUIDevice.shared.press(.home);Thread.sleep(forTimeInterval:1);app.activate()
        XCTAssertTrue(app.staticTexts["ATIVO"].waitForExistence(timeout:10))
        app.buttons["Fechar"].firstMatch.tap();openTuner(app)
        XCTAssertTrue(app.staticTexts["ATIVO"].waitForExistence(timeout:10))
        app.buttons["Fechar"].firstMatch.tap()
    }
    private func openTuner(_ app:XCUIApplication){
        XCTAssertTrue(app.buttons["Menu principal"].waitForExistence(timeout:10));app.buttons["Menu principal"].tap()
        XCTAssertTrue(app.buttons["AFINADOR"].waitForExistence(timeout:5));app.buttons["AFINADOR"].tap()
    }
    func testNavigationPlaybackAndTools() throws {
        let app=XCUIApplication();app.launchArguments=["--ui-fixture"];app.launch()
        XCTAssertTrue(app.staticTexts["Sessão de teste iOS"].firstMatch.waitForExistence(timeout:20))
        app.staticTexts["Sessão de teste iOS"].firstMatch.tap()
        XCTAssertTrue(app.buttons["VELOCIDADE 100%"].waitForExistence(timeout:10))
        app.buttons["PLAY"].tap()
        XCTAssertTrue(app.buttons["PAUSAR"].waitForExistence(timeout:5))
        app.buttons["Menu principal"].tap()
        XCTAssertTrue(app.buttons["PROJETOS"].waitForExistence(timeout:5))
        app.buttons["PROJETOS"].tap()
        XCTAssertTrue(app.buttons["PAUSAR"].waitForExistence(timeout:5))
        app.buttons["VELOCIDADE 100%"].tap()
        app.buttons["80%"].tap()
        let close=app.buttons["Fechar"].firstMatch;close.tap()
        XCTAssertTrue(app.buttons["VELOCIDADE 80%"].waitForExistence(timeout:90))
        attach("playback-portrait")
        XCUIDevice.shared.orientation = .landscapeLeft
        Thread.sleep(forTimeInterval:1)
        attach("playback-landscape")
        app.buttons["Menu principal"].tap();app.buttons["AFINADOR"].tap()
        XCTAssertTrue(app.staticTexts["TOQUE UMA CORDA"].waitForExistence(timeout:5))
        attach("tuner-landscape")
        XCUIDevice.shared.orientation = .portrait
        Thread.sleep(forTimeInterval:1)
        attach("tuner-portrait")
        app.buttons["Fechar"].firstMatch.tap()
        XCTAssertTrue(app.buttons["PLAY"].waitForExistence(timeout:5))
        app.buttons["Menu principal"].tap();app.buttons["PLANO / PAGAMENTO"].tap()
        XCTAssertTrue(app.staticTexts["SeuNomeNoApp"].waitForExistence(timeout:5))
        app.buttons["Fechar"].firstMatch.tap()
        app.buttons["Menu principal"].tap();app.buttons["CENTRAL DE DÚVIDAS"].tap()
        XCTAssertTrue(app.staticTexts["Importar e projetos"].waitForExistence(timeout:5))
    }
    private func attach(_ name:String){let a=XCTAttachment(screenshot:XCUIScreen.main.screenshot());a.name=name;a.lifetime = .keepAlways;add(a)}
}
