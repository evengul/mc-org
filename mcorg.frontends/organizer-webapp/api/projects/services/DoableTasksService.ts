/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AddTaskRequest } from '../models/AddTaskRequest';
import type { GenericResponse } from '../models/GenericResponse';
import type { SimpleProjectResponse } from '../models/SimpleProjectResponse';

import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';

export class DoableTasksService {

  /**
   * Add a task to a project
   * @param projectId 
   * @param requestBody 
   * @returns GenericResponse Created
   * @throws ApiError
   */
  public static add(
projectId: string,
requestBody: AddTaskRequest,
): CancelablePromise<GenericResponse> {
    return __request(OpenAPI, {
      method: 'POST',
      url: '/api/v1/project/{projectId}/task',
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
   * Reprioritize a task in a project
   * @param projectId 
   * @param taskId 
   * @param requestBody 
   * @returns GenericResponse OK
   * @throws ApiError
   */
  public static reprioritize(
projectId: string,
taskId: string,
requestBody: 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE',
): CancelablePromise<GenericResponse> {
    return __request(OpenAPI, {
      method: 'PATCH',
      url: '/api/v1/project/{projectId}/task/{taskId}/reprioritize',
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
   * Rename a task in a project
   * @param projectId 
   * @param taskId 
   * @param requestBody 
   * @returns GenericResponse OK
   * @throws ApiError
   */
  public static rename(
projectId: string,
taskId: string,
requestBody: string,
): CancelablePromise<GenericResponse> {
    return __request(OpenAPI, {
      method: 'PATCH',
      url: '/api/v1/project/{projectId}/task/{taskId}/rename',
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
   * Mark a task as incomplete
   * @param projectId 
   * @param taskId 
   * @returns GenericResponse OK
   * @throws ApiError
   */
  public static uncomplete(
projectId: string,
taskId: string,
): CancelablePromise<GenericResponse> {
    return __request(OpenAPI, {
      method: 'PATCH',
      url: '/api/v1/project/{projectId}/task/{taskId}/incomplete',
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

  /**
   * Convert to a project
   * @param projectId 
   * @param taskId 
   * @returns SimpleProjectResponse Created
   * @throws ApiError
   */
  public static convert(
projectId: string,
taskId: string,
): CancelablePromise<SimpleProjectResponse> {
    return __request(OpenAPI, {
      method: 'PATCH',
      url: '/api/v1/project/{projectId}/task/{taskId}/convert',
      query: {
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

  /**
   * Mark a task as complete
   * @param projectId 
   * @param taskId 
   * @returns GenericResponse OK
   * @throws ApiError
   */
  public static complete(
projectId: string,
taskId: string,
): CancelablePromise<GenericResponse> {
    return __request(OpenAPI, {
      method: 'PATCH',
      url: '/api/v1/project/{projectId}/task/{taskId}/complete',
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

  /**
   * Remove a task from a project with its ID
   * @param projectId 
   * @param taskId 
   * @returns GenericResponse OK
   * @throws ApiError
   */
  public static remove(
projectId: string,
taskId: string,
): CancelablePromise<GenericResponse> {
    return __request(OpenAPI, {
      method: 'DELETE',
      url: '/api/v1/project/{projectId}/task/{taskId}',
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
