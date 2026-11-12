# Tally

Tally is an Android app for keeping track of what you spend. Its main feature is that you don't have to type purchases in. It reads the notifications your bank, card and payment apps already send, picks out the purchases, payments and deposits, and adds them for you to check.

It's built for one person on one phone. There are no accounts, no server and no bank logins. Everything stays in a database on the device.

## What's in the app

### Overview

The selected month at a glance: what you've spent, your income, and the net between them. If any categories have budgets, it also shows how much of those budgets is left. Below that:

- **Review card:** shows when captured purchases are waiting to be checked.
- **Auto-capture card:** shows until notification access is turned on.
- **Where it went:** your top five spending categories.
- **Recent:** the five latest transactions.

The month switcher at the top is shared. Changing the month on one tab changes it on every tab.

### Activity

Every transaction in the month, grouped by day, with each day's spending. A ⚡ marks one that was captured from a notification, and "To review" marks one you haven't checked yet. "Not counted yet" marks a capture that isn't in your totals until you keep it (see [Review](#review)). Tap a transaction to edit or delete it, or tap **+** to add one by hand.

When you type a merchant on the add form, Tally fills in the category that merchant usually gets. If you pick a category yourself, it stops suggesting.

#### Exporting to a spreadsheet

Tap **⋮ → Export to spreadsheet** and choose the selected month, its whole year, or everything. Android then asks where to save the file. It can go in Downloads, Google Drive, or any other place the save dialog offers. Once it's saved, **Open** in the confirmation opens the file in your spreadsheet app.

The file is an Excel workbook (.xlsx), which Google Sheets, Excel, Numbers and LibreOffice all open. It has one row per transaction, oldest first, with these columns:

- **Date** and **Time**, stored as real dates and times so you can sort them.
- **Merchant**, **Category** and **Note**. A transaction with no category says "Uncategorized".
- **Spent** and **Income**. Each transaction fills one of the two, like a bank statement, so both columns add up to positive totals.
- **Source:** "Added by hand", or the app the purchase was captured from.
- **Status:** "To review" or "Not counted yet" for captures you haven't checked. It's blank once you've kept them.

The header row stays in view while you scroll and has filters. Captures that don't count yet are still listed. Filter out "Not counted yet" under Status and your totals will match the app's. The notification's own text isn't exported.

### Budgets

Each spending category can have a monthly budget, which applies every month. A category's bar is green while there's room, amber from 80% and red once you're over. Spending with no category is listed separately so you can see what isn't counted against a budget.

Tally starts with nine spending categories (Groceries, Dining, Transport, Shopping, Bills, Entertainment, Health, Travel, Other) and three income categories (Paycheck, Refunds, Other income). You can rename, re-icon or delete any of them and add your own. Deleting a category leaves its transactions in place, uncategorized.

### Auto-capture

This tab is where you turn capture on and see what it's doing:

- **Status:** whether Tally has notification access, with a button to the system switch.
- **Apps:** each app Tally has captured from, with a switch to stop reading it.
- **Merchant rules:** the categories Tally has learned per merchant. You can delete any of them.
- **Try a notification:** paste an alert's text to see what Tally would record from it, without saving anything.

## How auto-capture works

Turning on auto-capture gives Tally Android's **Notification access**, which lets an app see notifications as they're posted. Tally checks each new notification and keeps only the ones that look like money moving. It ignores everything else and stores nothing about it.

A notification counts when it has both of these:

- **An amount with a currency:** $, £, €, ₹, Rs, or a code like USD.
- **A word that means money moved:** "spent", "purchase", "charged", "transaction", "paid", "sent", "debited". Money coming in is recognized by words like "received", "deposit", "refund" and "paid you".

Some alerts are skipped even when they mention money: promotions ("$10 off"), declined cards, cash back and rewards, bills that are due, payment confirmations, money requests, scheduled or upcoming charges, and one-time codes.

The merchant comes from the alert's wording, such as "at STARBUCKS", "transaction with TARGET", "You paid Jordan Lee", "from Sam" or "to COMCAST". Tally cleans it up along the way. It strips store numbers and processor prefixes like `SQ *`, and turns ALL-CAPS names into normal case. Tap-to-pay wallets often put the merchant in the title and only the amount in the text, so Tally uses the title when it isn't just the app's name.

Some apps send alerts with no wording at all, and Tally reads those by their layout:

- **CIBC:** no title, and a text of three lines: the card ("CIBC Dividend Visa Card, 1234"), the merchant and the amount.
- **Samsung Wallet:** the card's name as the title, and a text that is only the merchant and the amount ("CANADIAN TIRE #608 $13.42").

Chat and SMS notifications are only recorded if they also read like a bank alert, mentioning a card, account, transaction or similar. A friend texting "I paid $40 for the tickets" isn't recorded. The wordless layouts above are never read from a chat or SMS, since anyone can type a card, a merchant and an amount.

### One purchase, two alerts

A purchase is often reported twice: by the wallet you tapped with and by the card's bank, or by PayPal and by the card behind it. Tally keeps one transaction. The first alert creates it, and an alert from another app within five minutes is added to it when either of these holds:

- **Same amount and merchant.** The amounts are equal and the merchants' names start alike, or one alert has no merchant.
- **The card names the other app.** A card alert reading "PAYPAL *CORNER BOOKS" belongs with PayPal's own alert even when the amounts differ, as they do when PayPal charges in another currency. The transaction then takes the card's amount and PayPal's name for the merchant.

Each app's alert can join a transaction once, so two purchases of the same amount in a row stay two. Both alerts' text is kept on the transaction. Once you've reviewed a transaction, a late second alert is still attached to it but changes nothing you checked.

### Review

Captured transactions aren't trusted blindly. Each one waits in **Review** with the notification's text beside it, where you can:

- **Keep** it, or use **Keep all**.
- **Change its category.** Tally learns the merchant from this.
- **Discard** it if it wasn't a purchase. Tally remembers that notification so it isn't recorded again if the app posts it again. Undo is available briefly.
- **Tap it** to edit the amount, merchant, date or note.

A captured transaction counts toward your totals and budgets as soon as it arrives, as long as you've kept a purchase from that app before (with **Keep**, **Keep all**, or by saving it from the edit form). The first captures from a new app wait in Review marked "Not counted yet" and count once you keep one of them; after that, the app's captures count right away. That way a new app, or a notification made to look like your bank's, can't change your numbers before you've seen it.

### Categories

A captured purchase gets its category from, in order:

1. **A merchant rule.** Changing a purchase's category in Review, or saving a transaction with a merchant and category, makes Tally remember that category for the merchant.
2. **A keyword guess** from the merchant's name, such as Starbucks → Dining, Shell → Transport or Netflix → Entertainment. The list is in `capture/Categorizer.kt`.
3. **Nothing.** It's left uncategorized for you to pick.

### Turning it on

Tap **Turn on** on the Overview or Auto-capture tab, then switch Tally on in the system's Notification access page.

On Android 13 and later, an app installed from outside an app store (including one installed from Android Studio as an APK) may have that switch greyed out. To fix it, open the app's **App info**, tap **⋮**, choose **Allow restricted settings**, and try again. The Auto-capture tab links to App info.

Only notifications posted while access is on are read. Notifications already in the shade when you turn it on are left alone.

## Limits

- **Wording:** the parser is written for English alerts in the usual US style. An app that phrases alerts differently may not be recognized. **Try a notification** shows whether a given alert is.
- **Length:** a notification over 1,000 characters, title and text together, is skipped. Real alerts are a few lines long. Longer text is an email or a chat, and reading it would be slow.
- **Currency:** amounts are shown in the phone's currency, and nothing is converted. A captured "€12.00" is stored as 12.00 like any other amount.
- **Notification access:** if you turn it off, or Android revokes it, nothing is captured until it's back on. The Auto-capture tab shows the current state.
- **Transfers:** moving money between your own accounts looks like spending on one side and income on the other. Discard whichever side you don't want counted.

## Code map

| Where | What |
| --- | --- |
| `capture/PurchaseParser.kt` | Reads a notification's title and text and returns an amount, direction and merchant, or null. Pure Kotlin. |
| `capture/Categorizer.kt` | Keyword guesses from merchant names, and merchant-rule keys. |
| `capture/PurchaseRecorder.kt` | Saves captures (deduplicated, skipping turned-off apps and dismissed notifications), resolves categories, learns rules. |
| `capture/PurchaseNotificationListener.kt` | The `NotificationListenerService` that hands notifications to the recorder. |
| `capture/NotificationAccess.kt` | Checks for notification access and opens its settings page. |
| `data/` | Room database: `categories`, `transactions`, `merchant_rules`, `source_apps`, `dismissed_captures`. Schemas are exported to `app/schemas`. |
| `export/Xlsx.kt` | Writes a one-sheet .xlsx workbook with typed cells. Pure Kotlin, no library. |
| `export/TxnSheet.kt` | Turns transactions into the export's rows and columns, and defines the periods you can export. |
| `export/TxnExporter.kt` | Reads a period's transactions and writes the file to the place the user picked. |
| `ui/` | Compose screens, one package per tab plus `review` and the transaction form. |
| `util/Money.kt` | Formatting and parsing amounts. Amounts are stored as whole cents in a `Long`. |

## Building and testing

Open the folder in Android Studio, or build from the command line:

```bash
./gradlew :app:assembleDebug
```

Unit tests cover the parser (real-world alert formats, plus things that must be skipped), the categorizer, money parsing and the spreadsheet export:

```bash
./gradlew :app:testDebugUnitTest
```

Instrumented tests cover the recorder against a real database (deduplication, dismissing, turned-off apps and merchant rules) and the exporter. They need a device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

By default this uninstalls Tally when it's done, which deletes its data on that device. To keep the app and its data, add `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`.

To try capture on an emulator without a bank app, grant access and post a fake alert from the shell:

```bash
adb shell cmd notification allow_listener com.example.tally/com.example.tally.capture.PurchaseNotificationListener
adb shell "cmd notification post -t 'Chase' test1 'You spent \$12.34 at STARBUCKS'"
```
