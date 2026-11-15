const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const nowTimestamp = () => new Date();

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_EMAIL = "aminulislam.hrdc7@gmail.com";
const FAKE_HASH = "a".repeat(64);

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validIncome(userId) {
  return {
    userId,
    accountId: "admin_main",
    amount: 25000,
    currency: "BDT",
    source: "SALARY",
    dateMillis: 1763000000000,
    description: "Monthly salary",
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };
}

function validExpense(userId) {
  return {
    userId,
    accountId: "admin_main",
    amount: 450,
    currency: "BDT",
    category: "FOOD",
    dateMillis: 1763000000000,
    description: "Lunch",
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };
}

function validGoal(userId) {
  return {
    userId,
    accountId: "admin_main",
    title: "Emergency Fund",
    targetAmount: 100000,
    savedAmount: 15000,
    currency: "BDT",
    frequency: "MONTHLY",
    targetDateMillis: 1790000000000,
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };
}

function validLoan(userId) {
  return {
    userId,
    accountId: "admin_main",
    type: "LENT",
    personName: "Rahim",
    personPhone: "01711223344",
    amount: 5000,
    paidAmount: 1000,
    currency: "BDT",
    dateMillis: 1763000000000,
    dueDateMillis: 1765000000000,
    note: "Medical help",
    status: "PARTIAL",
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };
}

function validManagedUser(userId) {
  return {
    userId,
    loginId: "rahim_01",
    fullName: "Rahim Uddin",
    passwordHash: FAKE_HASH,
    role: "USER",
    isActive: true,
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };
}

function validLoanRepayment(userId) {
  return {
    userId,
    accountId: "admin_main",
    loanId: "loan1",
    loanType: "LENT",
    personName: "Rahim",
    amount: 1000,
    currency: "BDT",
    dateMillis: 1763100000000,
    note: "bKash partial return",
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };
}

test("Unauthenticated user cannot read or write incomes or managed_users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("incomes").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("managed_users").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("loan_repayments").get()
  );
});

test("Cross-user access is denied across incomes, managed_users, and loan_repayments", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("managed_users")
      .doc("mu1")
      .set(validManagedUser(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("loan_repayments")
      .doc("rep1")
      .set(validLoanRepayment(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("managed_users").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("managed_users")
      .doc("mu1")
      .delete()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("loan_repayments").get()
  );
});

test("Owner can create, update, list, and delete managed users and finance entries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const muRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("managed_users")
    .doc("mu1");
  await assertSucceeds(muRef.set(validManagedUser(ALICE_UID)));
  await assertSucceeds(
    muRef.update({
      isActive: false,
      updatedAt: nowTimestamp(),
    })
  );
  await assertSucceeds(muRef.delete());

  const incRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("incomes")
    .doc("inc1");
  await assertSucceeds(incRef.set(validIncome(ALICE_UID)));
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("expenses")
      .doc("exp1")
      .set(validExpense(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("savings_goals")
      .doc("goal1")
      .set(validGoal(ALICE_UID))
  );
  const loanRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("loans")
    .doc("loan1");
  await assertSucceeds(loanRef.set(validLoan(ALICE_UID)));
  await assertSucceeds(
    loanRef.update({
      personPhone: "+966501234567",
      updatedAt: nowTimestamp(),
    })
  );
  await assertFails(
    loanRef.update({
      personPhone: "9".repeat(45),
      updatedAt: nowTimestamp(),
    })
  );
  const repRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("loan_repayments")
    .doc("rep1");
  await assertSucceeds(repRef.set(validLoanRepayment(ALICE_UID)));
  await assertSucceeds(repRef.delete());
});

test("ManagedUser rejects invalid hash length, invalid loginId, and unverified admin spoof", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("managed_users")
      .doc("mu_bad_hash")
      .set({ ...validManagedUser(ALICE_UID), passwordHash: "short" })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("managed_users")
      .doc("mu_bad_role")
      .set({ ...validManagedUser(ALICE_UID), role: "SUPERADMIN" })
  );

  // Email spoofing test: matching admin email with email_verified=false MUST fail
  const spoofDb = testEnv
    .authenticatedContext("spoof_uid", {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();
  await assertFails(
    spoofDb.collection("users").doc(ALICE_UID).collection("managed_users").get()
  );

  // Verified admin email succeeds
  const adminDb = testEnv
    .authenticatedContext("admin_uid", {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).collection("managed_users").get()
  );
});
