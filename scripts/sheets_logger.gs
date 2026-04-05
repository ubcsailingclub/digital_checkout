/**
 * UBC Sailing Club — Checkout Logger
 * Deploy as a Google Apps Script Web App (execute as: Me, access: Anyone)
 * and paste the deployment URL into the kiosk's Sync Settings > Apps Script URL.
 *
 * Sheet: https://docs.google.com/spreadsheets/d/1_rwrA2d7mJvMnd99wtfv6Yubnx-5kMmHSw8RIMirbu0
 *
 * Tab "Checkout Log" headers (row 1):
 *   Timestamp | Event | Member | Crew | Craft | Code | Session ID | Party Size | ETR | Checked Out | Checked In | Duration (min) | Notes | Damage?
 *
 * Tab "Damage Reports" headers (row 1):
 *   Timestamp | Member | Craft | Session ID | Notes
 *
 * Column index reference for "Checkout Log":
 *   A=1  Timestamp
 *   B=2  Event
 *   C=3  Member
 *   D=4  Crew
 *   E=5  Craft
 *   F=6  Code
 *   G=7  Session ID
 *   H=8  Party Size
 *   I=9  ETR
 *   J=10 Checked Out
 *   K=11 Checked In
 *   L=12 Duration (min)
 *   M=13 Notes
 *   N=14 Damage?
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
      const crewStr = Array.isArray(data.crew) ? data.crew.join(', ') : '';
      sheet.appendRow([
        new Date(),      // A: Timestamp
        'Checkout',      // B: Event
        data.skipper,    // C: Member
        crewStr,         // D: Crew
        data.craft,      // E: Craft
        data.code,       // F: Code
        data.sessionId,  // G: Session ID
        data.partySize,  // H: Party Size
        etr,             // I: ETR
        checkedOut,      // J: Checked Out
        '',              // K: Checked In   (filled on checkin)
        '',              // L: Duration     (filled on checkin)
        '',              // M: Notes        (filled on checkin)
        ''               // N: Damage?      (filled on checkin)
      ]);

    } else if (data.type === 'checkin') {
      const sheet     = ss.getSheetByName(CHECKOUT_SHEET);
      const checkedIn = new Date(data.checkinEpoch);
      const values    = sheet.getDataRange().getValues();
      for (let i = 1; i < values.length; i++) {
        if (String(values[i][6]) === String(data.sessionId)) {  // col G = index 6
          const row = i + 1;
          sheet.getRange(row, 2).setValue('Completed');                  // B: Event
          sheet.getRange(row, 11).setValue(checkedIn);                   // K: Checked In
          sheet.getRange(row, 12).setValue(data.durationMin);            // L: Duration (min)
          sheet.getRange(row, 13).setValue(data.notes || '');            // M: Notes
          sheet.getRange(row, 14).setValue(data.damage ? 'Yes' : 'No'); // N: Damage?
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
