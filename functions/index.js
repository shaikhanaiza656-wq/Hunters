const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { RtcTokenBuilder, RtcRole } = require("agora-access-token");
const admin = require("firebase-admin");

admin.initializeApp();

// Real Agora credentials, kept server-side only (set via:
//   firebase functions:secrets:set AGORA_APP_ID
//   firebase functions:secrets:set AGORA_APP_CERTIFICATE
// — get both from https://console.agora.io -> your project). The client
// never sees the App Certificate, only the short-lived signed token below.
const agoraAppId = defineSecret("AGORA_APP_ID");
const agoraAppCertificate = defineSecret("AGORA_APP_CERTIFICATE");

// Phase 5 — real open-mic team voice (GDD voice requirement: "like Free
// Fire", an open call not push-to-talk). Called by VoiceRepository.kt right
// before joining a Gate's voice channel. channelName = the Gate's Firestore
// id, so everyone fighting the same Gate shares one live voice call.
exports.generateAgoraToken = onCall(
  { secrets: [agoraAppId, agoraAppCertificate] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Must be signed in to join voice chat.");
    }

    const channelName = request.data && request.data.channelName;
    if (!channelName || typeof channelName !== "string") {
      throw new HttpsError("invalid-argument", "channelName is required.");
    }

    // Phase 6 — Guild voice channels use "guild_{guildId}" as the channel
    // name. Unlike Gate channels (open to any signed-in player fighting that
    // Gate), a guild channel is private: only real current members of that
    // guild get a token. This is a real Firestore membership check, not a
    // client-trusted flag.
    if (channelName.startsWith("guild_")) {
      const guildId = channelName.substring("guild_".length);
      const guildDoc = await admin.firestore().collection("guilds").doc(guildId).get();
      if (!guildDoc.exists) {
        throw new HttpsError("not-found", "Guild not found.");
      }
      const memberUids = guildDoc.data().memberUids || [];
      if (!memberUids.includes(request.auth.uid)) {
        throw new HttpsError("permission-denied", "You are not a member of this guild.");
      }
    }

    const appId = agoraAppId.value();
    const appCertificate = agoraAppCertificate.value();
    // uid 0 = "open" token; the Agora SDK lets the client's local uid be
    // auto-assigned on join, so no client-side uid mapping is needed.
    const uid = 0;
    const expirationTimeInSeconds = 3600;
    const currentTimestamp = Math.floor(Date.now() / 1000);
    const privilegeExpiredTs = currentTimestamp + expirationTimeInSeconds;

    const token = RtcTokenBuilder.buildTokenWithUid(
      appId,
      appCertificate,
      channelName,
      uid,
      RtcRole.PUBLISHER,
      privilegeExpiredTs
    );

    return { token, appId, uid, expiresAt: privilegeExpiredTs };
  }
);

// Runs once a day. Deletes any guest (anonymous) account whose
// lastActiveAt in Firestore is older than 30 days, matching GDD rule:
// "Guest Account Rule: if inactive for 30 days, it will be automatically deleted."
exports.cleanupInactiveGuestAccounts = onSchedule("every 24 hours", async () => {
  const db = admin.firestore();
  const auth = admin.auth();

  const cutoff = admin.firestore.Timestamp.fromMillis(
    Date.now() - 30 * 24 * 60 * 60 * 1000
  );

  const staleGuestsSnapshot = await db
    .collection("guestAccounts")
    .where("isGuest", "==", true)
    .where("lastActiveAt", "<", cutoff)
    .get();

  if (staleGuestsSnapshot.empty) {
    console.log("No inactive guest accounts to delete.");
    return;
  }

  const deletions = staleGuestsSnapshot.docs.map(async (doc) => {
    const uid = doc.id;
    try {
      await auth.deleteUser(uid);
      await doc.ref.delete();
      console.log(`Deleted inactive guest account: ${uid}`);
    } catch (err) {
      console.error(`Failed to delete guest account ${uid}:`, err.message);
    }
  });

  await Promise.all(deletions);
});
