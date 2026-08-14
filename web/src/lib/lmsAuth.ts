import { signOut, type Auth } from "firebase/auth";

import { getFirebaseAuth } from "@/lib/firebase";

/**
 * Sign out and ignore stale /lms/me responses that resolve after logout.
 * Call sites should clear local `me`/`user` in onAuthStateChanged; this
 * bumps a generation so in-flight profile loads can no-op.
 */
let authGeneration = 0;

export function getAuthGeneration() {
  return authGeneration;
}

export function bumpAuthGeneration() {
  authGeneration += 1;
  return authGeneration;
}

export async function signOutFully(auth?: Auth) {
  bumpAuthGeneration();
  const a = auth ?? getFirebaseAuth();
  await signOut(a);
}
