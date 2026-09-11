import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, writeBatch, runTransaction } from 'firebase/firestore';

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-tri-force',
    firestore: { host: '127.0.0.1', port: 8080, rules: await readFile(new URL('../../firestore.rules', import.meta.url), 'utf8') }
  });
});
beforeEach(async () => { await env.clearFirestore(); });
after(async () => { await env?.cleanup(); });

test('unauthenticated clients cannot access profile or catalogue', async () => {
  const db = env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(db, 'users/alice')));
  await assertFails(setDoc(doc(db, 'users/alice'), { fullName: 'Test' }));
  await assertFails(getDoc(doc(db, 'exercises/squat')));
});

test('another account cannot read or write any user subtree', async () => {
  const db = env.authenticatedContext('bob').firestore();
  for (const path of ['users/alice', 'users/alice/workouts/day_1', 'users/alice/workout_sessions/s1', 'users/alice/exercise_history/squat', 'users/alice/custom_plans/p1']) {
    await assertFails(getDoc(doc(db, path)));
    await assertFails(setDoc(doc(db, path), { test: true }));
  }
});

test('owner can atomically create profile and all thirty days', async () => {
  const db = env.authenticatedContext('alice').firestore();
  const batch = writeBatch(db);
  batch.set(doc(db, 'users/alice'), { fullName: 'Test', createdTime: 100 });
  for (let day = 1; day <= 30; day++) batch.set(doc(db, `users/alice/workouts/day_${day}`), { dayIndex: day, exercises: [] });
  await assertSucceeds(batch.commit());
  assert.equal((await getDoc(doc(db, 'users/alice/workouts/day_30'))).data().dayIndex, 30);
});

test('authenticated catalogue reads succeed and client writes fail', async () => {
  const db = env.authenticatedContext('alice').firestore();
  await assertSucceeds(getDoc(doc(db, 'exercises/squat')));
  await assertFails(setDoc(doc(db, 'exercises/squat'), { name: 'Changed' }));
});

test('session transaction can read missing documents and write owner results', async () => {
  const db = env.authenticatedContext('alice').firestore();
  const profile = doc(db, 'users/alice');
  const day = doc(db, 'users/alice/workouts/day_1');
  const session = doc(db, 'users/alice/workout_sessions/session');
  const history = doc(db, 'users/alice/exercise_history/squat');
  await setDoc(profile, { createdTime: 100 });
  await setDoc(day, { exercises: [{ exerciseId: 'squat', status: 0 }] });
  await assertSucceeds(runTransaction(db, async tx => {
    await tx.get(profile);
    await tx.get(day);
    await tx.get(session);
    await tx.get(history);
    tx.set(session, { id: 'session', exerciseId: 'squat', completedAt: 200 });
    tx.update(day, { exercises: [{ exerciseId: 'squat', status: 1 }] });
    tx.set(history, { totalSessions: 1 });
  }));
  assert.equal((await getDoc(history)).data().totalSessions, 1);
});
