/**
 * UBC Sailing Club — Checkout Logger
 * Deploy as a Google Apps Script Web App (execute as: Me, access: Anyone)
 * and paste the deployment URL into the kiosk's Sync Settings > Apps Script URL.
 *
 * Sheet: https://docs.google.com/spreadsheets/d/1_rwrA2d7mJvMnd99wtfv6Yubnx-5kMmHSw8RIMirbu0
 *
 * Tab "Checkout Log" headers (row 1):
 *   Timestamp | Event | Member | Craft | Code | Session ID | Party Size | ETR | Checked Out | Checked In | Duration (min) | Notes | Damage?
 *
 * Tab "Damage Reports" headers (row 1):
 *   Timestamp | Member | Craft | Session ID | Notes
 *
 * Column index reference for "Checkout Log":
 *   A=1  Timestamp
 *   B=2  Event
 *   C=3  Member
 *   D=4  Craft
 *   E=5  Code
 *   F=6  Session ID
 *   G=7  Party Size
 *   H=8  ETR
 *   I=9  Checked Out
 *   J=10 Checked In
 *   K=11 Duration (min)
 *   L=12 Notes
 *   M=13 Damage?
 */

const SPREADSHEET_ID = '1_rwrA2d7mJvMnd99wtfv6Yubnx-5kMmHSw8RIMirbu0';
const CHECKOUT_SHEET = 'Checkout Log';
const DAMAGE_SHEET   = 'Damage Reports';

function doPost(e) {
  try {
    const data = JSON.parse(e.postData.contents);
    const ss   = SpreadsheetApp.openById(SPREADSHEET_ID);

    if (data.type === 'checkout') {
      const sheet      = ss.getSheetByName(CHECKOUT_SHEET);
      const checkedOut = new Date(data.checkoutEpoch);
      const etr        = data.etaEpoch ? new Date(data.etaEpoch) : '';
      sheet.appendRow([
        new Date(),      // A: Timestamp
        'Checkout',      // B: Event
        data.skipper,    // C: Member
        data.craft,      // D: Craft
        data.code,       // E: Code
        data.sessionId,  // F: Session ID
        data.partySize,  // G: Party Size
        etr,             // H: ETR
        checkedOut,      // I: Checked Out
        '',              // J: Checked In   (filled on checkin)
        '',              // K: Duration     (filled on checkin)
        '',              // L: Notes        (filled on checkin)
        ''               // M: Damage?      (filled on checkin)
      ]);

    } else if (data.type === 'checkin') {
      const sheet     = ss.getSheetByName(CHECKOUT_SHEET);
      const checkedIn = new Date(data.checkinEpoch);
      const values    = sheet.getDataRange().getValues();
      for (let i = 1; i < values.length; i++) {
        if (String(values[i][5]) === String(data.sessionId)) {  // col F = index 5
          const row = i + 1;
          sheet.getRange(row, 2).setValue('Completed');                  // B: Event
          sheet.getRange(row, 10).setValue(checkedIn);                   // J: Checked In
          sheet.getRange(row, 11).setValue(data.durationMin);            // K: Duration (min)
          sheet.getRange(row, 12).setValue(data.notes || '');            // L: Notes
          sheet.getRange(row, 13).setValue(data.damage ? 'Yes' : 'No'); // M: Damage?
          break;
        }
      }

    } else if (data.type === 'damage') {
      const sheet = ss.getSheetByName(DAMAGE_SHEET);
      sheet.appendRow([
        new Date(),      // A: Timestamp
        data.skipper,    // B: Member
        data.craft,      // C: Craft
        data.sessionId,  // D: Session ID
        data.notes       // E: Notes
      ]);
    }

    return ContentService
      .createTextOutput('OK')
      .setMimeType(ContentService.MimeType.TEXT);

  } catch (err) {
    return ContentService
      .createTextOutput('Error: ' + err)
      .setMimeType(ContentService.MimeType.TEXT);
  }
}

// Smoke-test: visit the deployment URL in a browser to confirm it's live
function doGet() {
  return ContentService
    .createTextOutput('UBC SC Checkout Logger is running.')
    .setMimeType(ContentService.MimeType.TEXT);
}
