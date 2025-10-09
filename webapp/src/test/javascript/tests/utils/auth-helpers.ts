/**
 * Authentication utilities for Playwright tests
 */
import { Page } from '@playwright/test';

export class AuthHelpers {
  constructor(private page: Page) {}

  /**
   * Sign in using the test authentication flow
   * This will create a fresh TestUser_{random} user
   */
  async signInAsTestUser(): Promise<void> {
    // Navigate to test sign-in endpoint which auto-creates a test user
    await this.page.goto('/auth/oidc/test-redirect');

    // Should be redirected to /app after successful authentication
    await this.page.waitForURL('/app');

    // Verify we're authenticated by checking for user-specific content
    await this.page.waitForSelector('body:not(:has-text("Sign In"))');
  }

  /**
   * Sign out the current user
   */
  async signOut(): Promise<void> {
    await this.page.goto('/auth/sign-out');

    // Should be redirected to landing page or sign-in page
    await this.page.waitForURL(/\/(auth\/sign-in)?$/);
  }

  /**
   * Check if user is currently authenticated
   */
  async isAuthenticated(): Promise<boolean> {
    try {
      await this.page.goto('/app');
      // If we can access /app without redirect, we're authenticated
      return this.page.url().includes('/app');
    } catch {
      return false;
    }
  }

  /**
   * Navigate to app and ensure authentication
   */
  async ensureAuthenticated(): Promise<void> {
    const isAuth = await this.isAuthenticated();
    if (!isAuth) {
      await this.signInAsTestUser();
    }
  }
}
