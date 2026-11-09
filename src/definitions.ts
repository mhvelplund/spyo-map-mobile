/**
 * Contract between the native SpyoNative plugin and the web app.
 *
 * Source of truth. Mirrored in spyo-map/packages/frontend/src/utils/native.ts — keep the two in sync.
 * The Android implementation is android/app/src/main/java/net/isntall/spyomap/SpyoNativePlugin.kt; an iOS
 * implementation must satisfy the same interface.
 */

export interface PickedPhoto {
  name: string;
  mimeType: string;
  size: number;
  /** Same-origin URL the page can fetch(): `<server url>/_capacitor_file_<absolute cache path>`. */
  webPath: string;
}

export interface PhotosResult {
  photos: PickedPhoto[];
  /** Display names of photos the app could not read (revoked grant, cloud-only item, rejected URI). */
  failed?: string[];
}

export interface ListenerHandle {
  remove(): Promise<void> | void;
}

export type SignInErrorCode = 'CANCELED' | 'NO_CREDENTIAL' | 'FAILED';

export interface SpyoNativePlugin {
  /** Google ID token (audience = the web OAuth client). Rejects with `code: SignInErrorCode`. */
  signIn(options: { silent: boolean }): Promise<{ idToken: string }>;
  /** Clears the "signed in before" flag and Credential Manager state. */
  signOut(): Promise<void>;
  hasSignedInBefore(): Promise<{ value: boolean }>;
  /** Opens the document picker (multi-select, originals with GPS). Resolves with [] when cancelled. */
  pickPhotos(): Promise<PhotosResult>;
  /** Drains photos shared to the app via the Android share sheet. */
  getSharedPhotos(): Promise<PhotosResult>;
  /** Opens the phone's photo gallery app. Rejects with `code: 'NO_APP'` when none is installed. */
  openGallery(): Promise<void>;
  /**
   * Fired when new photos are shared while the app is running. Through `window.Capacitor.Plugins` (no
   * @capacitor/core in the page) this returns the handle synchronously; via registerPlugin it is a Promise.
   */
  addListener(event: 'photosShared', cb: () => void): ListenerHandle | Promise<ListenerHandle>;
}
