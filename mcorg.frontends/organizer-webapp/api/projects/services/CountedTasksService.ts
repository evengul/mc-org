/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AddCountedTaskRequest } from '../models/AddCountedTaskRequest';
import type { GenericResponse } from '../models/GenericResponse';

import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';

export class CountedTasksService {

  /**
   * Add a counted task to a project
   * @param projectId 
   * @param requestBody 
   * @returns GenericResponse OK
   * @throws ApiError
   */
  public static add1(
projectId: string,
requestBody: AddCountedTaskRequest,
): CancelablePromise<GenericResponse> {
    return __request(OpenAPI, {
      method: 'POST',
      url: '/api/project/{projectId}/counted-task',
      path: {
        'projectId': projectId,
      },
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        400: `Bad Request`,
        404: `Not Found`,
        405: `Method Not Allowed`,
        500: `Internal Server Error`,
      },
    });
  }

  /**
   * Reprioritize a counted task
   * @param projectId 
   * @param taskId 
   * @param requestBody 
   * @returns GenericResponse OK
   * @throws ApiError
   */
  public static reprioritize1(
projectId: string,
taskId: string,
requestBody?: 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE',
): CancelablePromise<GenericResponse> {
    return __request(OpenAPI, {
      method: 'PATCH',
      url: '/api/project/{projectId}/counted-task/{taskId}/reprioritize',
      path: {
        'projectId': projectId,
        'taskId': taskId,
      },
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        400: `Bad Request`,
        404: `Not Found`,
        405: `Method Not Allowed`,
        500: `Internal Server Error`,
      },
    });
  }

  /**
   * Rename a counted task
   * @param projectId 
   * @param taskId 
   * @param requestBody 
   * @returns GenericResponse OK
   * @throws ApiError
   */
  public static rename1(
projectId: string,
taskId: string,
requestBody: string,
): CancelablePromise<GenericResponse> {
    return __request(OpenAPI, {
      method: 'PATCH',
      url: '/api/project/{projectId}/counted-task/{taskId}/rename',
      path: {
        'projectId': projectId,
        'taskId': taskId,
      },
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        400: `Bad Request`,
        404: `Not Found`,
        405: `Method Not Allowed`,
        500: `Internal Server Error`,
      },
    });
  }

  /**
   * Set the work done for a counted task
   * @param projectId 
   * @param taskId 
   * @param requestBody 
   * @returns GenericResponse OK
   * @throws ApiError
   */
  public static doneMore(
projectId: string,
taskId: string,
requestBody: number,
): CancelablePromise<GenericResponse> {
    return __request(OpenAPI, {
      method: 'PATCH',
      url: '/api/project/{projectId}/counted-task/{taskId}/register-work',
      path: {
        'projectId': projectId,
        'taskId': taskId,
      },
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        400: `Bad Request`,
        404: `Not Found`,
        405: `Method Not Allowed`,
        500: `Internal Server Error`,
      },
    });
  }

  /**
   * Set the needed work for a counted task
   * @param projectId 
   * @param taskId 
   * @param requestBody 
   * @returns GenericResponse OK
   * @throws ApiError
   */
  public static needsMore(
projectId: string,
taskId: string,
requestBody: number,
): CancelablePromise<GenericResponse> {
    return __request(OpenAPI, {
      method: 'PATCH',
      url: '/api/project/{projectId}/counted-task/{taskId}/needs-more',
      path: {
        'projectId': projectId,
        'taskId': taskId,
      },
      body: requestBody,
      mediaType: 'application/json',
      errors: {
        400: `Bad Request`,
        404: `Not Found`,
        405: `Method Not Allowed`,
        500: `Internal Server Error`,
      },
    });
  }

  /**
   * Remove a counted task from a project
   * @param projectId 
   * @param taskId 
   * @returns GenericResponse OK
   * @throws ApiError
   */
  public static remove1(
projectId: string,
taskId: string,
): CancelablePromise<GenericResponse> {
    return __request(OpenAPI, {
      method: 'DELETE',
      url: '/api/project/{projectId}/counted-task/{taskId}',
      path: {
        'projectId': projectId,
        'taskId': taskId,
      },
      errors: {
        400: `Bad Request`,
        404: `Not Found`,
        405: `Method Not Allowed`,
        500: `Internal Server Error`,
      },
    });
  }

}
