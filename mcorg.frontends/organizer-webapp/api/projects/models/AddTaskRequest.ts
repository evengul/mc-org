/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type AddTaskRequest = {
  name: string;
  priority?: AddTaskRequest.priority;
};

export namespace AddTaskRequest {

  export enum priority {
    HIGH = 'HIGH',
    MEDIUM = 'MEDIUM',
    LOW = 'LOW',
    NONE = 'NONE',
  }


}
