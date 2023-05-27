/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ResourcePackRequest } from '../models/ResourcePackRequest';
import type { ResourcePackResponse } from '../models/ResourcePackResponse';
import type { ResourcePacksResponse } from '../models/ResourcePacksResponse';
import type { ResourceRequest } from '../models/ResourceRequest';

import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';

export class ResourcesService {

  /**
   * Retrieves all available resourcePacks for all versions
   * @returns ResourcePacksResponse OK
   * @throws ApiError
   */
  public static getAll(): CancelablePromise<ResourcePacksResponse> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/api/v1/resource-pack',
    });
  }

  /**
   * Create a new resourcePack
   * @param requestBody 
   * @returns any Created
   * @throws ApiError
   */
  public static create(
requestBody: ResourcePackRequest,
): CancelablePromise<any> {
    return __request(OpenAPI, {
      method: 'POST',
      url: '/api/v1/resource-pack',
      body: requestBody,
      mediaType: 'application/json',
    });
  }

  /**
   * Add a versioned URL to a resourcePack
   * @param packId 
   * @param requestBody 
   * @returns any OK
   * @throws ApiError
   */
  public static addVersionedUrl(
packId: string,
requestBody: ResourceRequest,
): CancelablePromise<any> {
    return __request(OpenAPI, {
      method: 'PATCH',
      url: '/api/v1/resource-pack/{packId}/resource',
      path: {
        'packId': packId,
      },
      body: requestBody,
      mediaType: 'application/json',
    });
  }

  /**
   * Get a single resource pack
   * @param packId 
   * @returns ResourcePackResponse OK
   * @throws ApiError
   */
  public static getResourcepack(
packId: string,
): CancelablePromise<ResourcePackResponse> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/api/v1/resource-pack/{packId}',
      path: {
        'packId': packId,
      },
    });
  }

  /**
   * Retrieves all resourcePacks for a version
   * @param version 
   * @returns ResourcePacksResponse OK
   * @throws ApiError
   */
  public static getResourcesForVersion(
version: string,
): CancelablePromise<ResourcePacksResponse> {
    return __request(OpenAPI, {
      method: 'GET',
      url: '/api/v1/resource-pack/version/{version}',
      path: {
        'version': version,
      },
    });
  }

}
