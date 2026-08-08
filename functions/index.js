const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

/**
 * Triggers when a new expense is added to a group.
 * Sends a push notification to every group member except the one who added it.
 */
exports.onGroupExpenseAdded = functions.firestore
  .document("groups/{code}/expenses/{expenseId}")
  .onCreate(async (snap, context) => {
    const expense = snap.data();
    const { code } = context.params;

    if (!expense) return;

    const paidByName = expense.paidByName || "Someone";
    const description = expense.description || "an expense";
    const amount = expense.amount ? `₹${Number(expense.amount).toFixed(2)}` : "";

    // Fetch group to get memberUids
    const groupDoc = await db.collection("groups").doc(code).get();
    if (!groupDoc.exists) return;

    const memberUids = groupDoc.data().memberUids || [];
    // Don't notify the person who added the expense
    const recipientUids = memberUids.filter((uid) => uid !== expense.paidByUid);
    if (recipientUids.length === 0) return;

    // Fetch FCM tokens for all recipients in parallel
    const tokenSnaps = await Promise.all(
      recipientUids.map((uid) => db.collection("users").doc(uid).get())
    );
    const tokens = tokenSnaps
      .map((s) => s.data()?.fcmToken)
      .filter((t) => typeof t === "string" && t.length > 0);

    if (tokens.length === 0) return;

    const message = {
      notification: {
        title: `${paidByName} added an expense`,
        body: `${description}${amount ? ` • ${amount}` : ""} — check your share`,
      },
      data: {
        groupCode: code,
        expenseId: snap.id,
        type: "group_expense",
      },
      tokens,
    };

    const response = await messaging.sendEachForMulticast(message);
    // Clean up stale tokens
    const staleTokens = [];
    response.responses.forEach((r, i) => {
      if (!r.success && r.error?.code === "messaging/registration-token-not-registered") {
        staleTokens.push(tokens[i]);
      }
    });
    if (staleTokens.length > 0) {
      const batch = db.batch();
      tokenSnaps.forEach((s) => {
        if (staleTokens.includes(s.data()?.fcmToken)) {
          batch.update(s.ref, { fcmToken: admin.firestore.FieldValue.delete() });
        }
      });
      await batch.commit();
    }
  });
