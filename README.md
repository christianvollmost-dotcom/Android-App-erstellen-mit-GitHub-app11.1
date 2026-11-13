# Trade Journal (Android)

Ein erster Android-MVP für ein Trading-Journal: Trades können lokal erfasst werden; die App zeigt einen einfachen realisierten P&L und enthält den verschlüsselten Übergabepunkt für eine spätere Backend-Anbindung.

## Status und wichtige Grenzen

Dieses Repository enthält **noch keinen produktionsreifen Börsen-Backend-Dienst**. Daher funktionieren aktuell weder der Abruf von Binance/Bitunix/MEXC/WEEX-Daten noch Cloud-Synchronisierung, TradingView-Order-Overlays, Steuerberechnung, Futures-/Earn-/Einzahlungs-/Auszahlungsimporte oder ein serverseitiger Worker. Die manuellen Journal-Einträge werden nur auf dem Gerät gespeichert und gehen bei Deinstallation verloren. Bitte keine echten API-Schlüssel in einer nicht konfigurierten oder ungeprüften Instanz eingeben.

Die Börsenverbindung im MVP verschlüsselt API-Key und Secret jeweils mit RSA-OAEP-SHA-256 (MGF1 SHA-256) und sendet sie ausschließlich an eine konfigurierte HTTPS-URL. Die App speichert diese Zugangsdaten nicht. Der Backend-Endpunkt und die Schlüsselverwaltung müssen vor echtem Einsatz implementiert und unabhängig sicherheitsgeprüft werden.

## Android-App bauen

Voraussetzungen: Android Studio oder JDK 17 mit Android SDK, Gradle-Wrapper (in diesem Repository noch nicht enthalten; Android Studio kann ihn erzeugen).

Backend-Parameter über Gradle-Properties setzen:

```bash
./gradlew assembleDebug \
  -PbackendUrl=https://api.example.com \
  -PbackendKeyId=key-2026-01 \
  -PbackendPublicKey='-----BEGIN PUBLIC KEY-----...'
```

Ohne Backend-Konfiguration kann die App als lokales Journal gestartet werden; „Börse sicher verbinden“ zeigt dann eine Konfigurationsmeldung.

## Backend-Vertrag für den Verbindungs-Prototyp

`POST /v1/exchanges/connections` mit JSON:

```json
{
  "exchange": "binance",
  "key_id": "key-2026-01",
  "api_key_encrypted": "<base64 RSA-OAEP ciphertext>",
  "secret_encrypted": "<base64 RSA-OAEP ciphertext>"
}
```

Das Backend muss TLS erzwingen, die RSA-Privatschlüssel in einem KMS/HSM verwalten, Ciphertext nur im Arbeitsspeicher entschlüsseln und Exchange-Zugangsdaten serverseitig verschlüsselt speichern. Es braucht außerdem Benutzer-Authentifizierung und Autorisierung, Rate Limits, Audit ohne Geheimnisse, Schlüsselrotation, Lösch-/Widerrufsabläufe, Worker-/Job-Queue und eine fest konfigurierte Egress-IP. Der aktuelle Android-Prototyp hat noch keinen Login-/Bearer-Token-Flow; der Endpunkt darf deshalb nicht öffentlich Schlüssel annehmen. RSA-OAEP hat eine begrenzte Nachrichtengröße und ist hier nur für kurze API-Zugangsdaten vorgesehen.

Für Börsen-Keys nur erforderliche Lese-Rechte vergeben, IP-Allowlisting auf die dedizierte Server-Egress-IP aktivieren und Handels-/Auszahlungsrechte deaktivieren.

## Geplante nächste Ausbaustufen

1. Backend mit Login, verschlüsseltem Cloud-Speicher, Job-Queue und festem Egress bereitstellen.
2. Börsenadapter und paginierte, idempotente Importe mit Wiederaufnahme, Limits und Lösch-/Widerrufsabläufen ergänzen.
3. Trades, Gebühren, Funding und weitere Datentypen versioniert synchronisieren; lokale Offline-Kopie verschlüsseln.
4. Chart-Integration und österreichische Steuerregeln mit nachvollziehbaren Quellen/Exporten ergänzen und fachlich prüfen lassen.

Keine Steuer- oder Anlageberatung. Eine Steuerfunktion muss vor Verwendung für eine Erklärung von einer österreichischen Steuerberatung geprüft werden.
