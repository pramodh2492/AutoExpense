# Privacy Policy - AutoExpense

**Last updated: July 15, 2026**

## Overview

AutoExpense ("the App") reads your SMS messages to automatically track your financial transactions. Your privacy is important to us.

## Data Collection

### What we read:
- SMS messages from known bank sender IDs (e.g., AXISBK, HDFCBK, SBIINB)
- Only transactional messages containing debit/credit keywords

### What we DO NOT read:
- Personal messages
- OTPs or verification codes
- Promotional SMS
- Messages from non-banking senders

## Data Storage

- **On-device processing**: All SMS parsing and categorization happens locally on your device
- **Cloud sync (optional)**: If you enable Firebase sync, your transaction data (amount, merchant, category, date) is stored in Google Firebase under your authenticated account
- **Raw SMS text**: Stored only on your device for reference, never uploaded to any server

## Data Sharing

We do NOT:
- Sell your data to third parties
- Share your financial information with advertisers
- Upload your SMS to external servers for processing
- Track your location or behavior

## Permissions Used

| Permission | Purpose |
|---|---|
| READ_SMS | Read bank transaction messages for expense tracking |
| RECEIVE_SMS | Detect new transactions in real-time |
| POST_NOTIFICATIONS | Send spending alerts and daily reminders |
| INTERNET | Optional cloud backup via Firebase |

## Data Deletion

- All data can be deleted by clearing app data or uninstalling the app
- Cloud data can be deleted from Settings within the app

## Security

- Data is encrypted in transit (HTTPS/TLS)
- Firebase data is protected by authentication
- No server-side storage of raw SMS content

## Contact

For questions about this privacy policy, contact: [your-email@example.com]

## Changes

We may update this policy. Changes will be posted within the app.
