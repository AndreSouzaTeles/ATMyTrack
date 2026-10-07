import XCTest
final class NavigationTests:XCTestCase {
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
        attach("playback-landscape")
        app.buttons["Menu principal"].tap();app.buttons["AFINADOR"].tap()
        XCTAssertTrue(app.staticTexts["TOQUE UMA CORDA"].waitForExistence(timeout:5))
        attach("tuner-landscape")
        XCUIDevice.shared.orientation = .portrait
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
