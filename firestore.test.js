const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_1";
const BOB_UID = "bob_uid_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validOrgPayload(userId, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId,
    name: "ООО «Вектор»",
    inn: "7707083893",
    kpp: "773601001",
    ogrn: "1027700132195",
    address: "г. Москва, ул. Вавилова, д. 19",
    phone: "+7 (495) 500-55-50",
    bankName: "ПАО Сбербанк",
    bik: "044525225",
    checkingAccount: "40702810938000012345",
    correspondentAccount: "30101810400000000225",
    directorTitle: "Генеральный директор",
    directorName: "Иванов И.И.",
    accountantName: "Петрова А.С.",
    defaultVatRate: 20,
    defaultProfile: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validPartyPayload(userId, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId,
    name: "ООО «Альфа Поставка»",
    inn: "7736207543",
    kpp: "773601001",
    ogrn: "1027700229193",
    address: "г. Москва, Ленинский пр-т, д. 10",
    phone: "+7 (495) 111-22-33",
    bankName: "АО «ТБанк»",
    bik: "044525974",
    checkingAccount: "40702810000000099999",
    correspondentAccount: "30101810145250000974",
    directorName: "Сидоров П.П.",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validDocumentPayload(userId, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId,
    docType: "INVOICE",
    docNumber: "104",
    docDate: "23.11.2026",
    basisDoc: "Договор № 12 от 01.11.2026",
    paymentDoc: "П/П № 45 от 22.11.2026",
    status: "DRAFT",
    sellerName: "ООО «Вектор»",
    sellerInn: "7707083893",
    sellerKpp: "773601001",
    sellerOgrn: "1027700132195",
    sellerAddress: "г. Москва, ул. Вавилова, д. 19",
    sellerPhone: "+7 (495) 500-55-50",
    sellerBankName: "ПАО Сбербанк",
    sellerBik: "044525225",
    sellerCheckingAccount: "40702810938000012345",
    sellerCorrAccount: "30101810400000000225",
    sellerDirectorTitle: "Генеральный директор",
    sellerDirectorName: "Иванов И.И.",
    sellerAccountantName: "Петрова А.С.",
    buyerName: "ООО «Альфа Поставка»",
    buyerInn: "7736207543",
    buyerKpp: "773601001",
    buyerOgrn: "1027700229193",
    buyerAddress: "г. Москва, Ленинский пр-т, д. 10",
    buyerPhone: "+7 (495) 111-22-33",
    buyerBankName: "АО «ТБанк»",
    buyerBik: "044525974",
    buyerCheckingAccount: "40702810000000099999",
    buyerCorrAccount: "30101810145250000974",
    consignorInfo: "Он же",
    consigneeInfo: "Он же",
    items: [
      {
        name: "Серверное оборудование",
        unitCode: "796",
        unitName: "шт",
        quantity: 2,
        priceWithoutVat: 50000,
        vatRate: 20,
        vatAmount: 20000,
        totalWithVat: 120000,
        countryCode: "643",
        countryName: "Россия",
        customsDecl: "-",
      },
    ],
    subtotalWithoutVat: 100000,
    totalVat: 20000,
    totalWithVat: 120000,
    totalQuantity: 2,
    totalPlaces: 1,
    totalWeightKg: 14.5,
    amountInWords: "Сто двадцать тысяч рублей 00 копеек",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("organizations").get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("organizations")
      .doc("org_1")
      .set(validOrgPayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("documents").get());
});

test("Owner can CRUD organizations and counterparties with valid payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const orgRef = aliceDb.collection("users").doc(ALICE_UID).collection("organizations").doc("org_1");
  await assertSucceeds(orgRef.set(validOrgPayload(ALICE_UID)));
  await assertSucceeds(orgRef.update({ phone: "+7 (495) 000-00-01", updatedAt: new Date(Date.now() - 500) }));

  const partyRef = aliceDb.collection("users").doc(ALICE_UID).collection("counterparties").doc("party_1");
  await assertSucceeds(partyRef.set(validPartyPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("counterparties").get());
});

test("Cross-user access is strictly denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("documents")
      .doc("doc_bob")
      .set(validDocumentPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("documents").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("documents").doc("doc_bob").get());
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("documents")
      .doc("doc_bob")
      .update({ docNumber: "999", updatedAt: new Date(Date.now() - 500) })
  );
});

test("Document lifecycle: DRAFT updates and ISSUED terminal lock, plus ghost/id rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docsCol = aliceDb.collection("users").doc(ALICE_UID).collection("documents");

  // Reject extra @DocumentId 'id' field
  await assertFails(docsCol.doc("doc_bad").set(validDocumentPayload(ALICE_UID, { id: "doc_bad" })));

  // Reject future timestamp
  await assertFails(
    docsCol.doc("doc_future").set(validDocumentPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) }))
  );

  // Create valid DRAFT
  const docRef = docsCol.doc("doc_1");
  await assertSucceeds(docRef.set(validDocumentPayload(ALICE_UID)));

  // Update DRAFT fields
  await assertSucceeds(
    docRef.update({
      docNumber: "105",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Transition DRAFT -> ISSUED
  await assertSucceeds(
    docRef.update({
      status: "ISSUED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Terminal state lock: ISSUED document cannot be edited
  await assertFails(
    docRef.update({
      docNumber: "106",
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Owner can still delete
  await assertSucceeds(docRef.delete());
});
