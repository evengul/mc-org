/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type AddCountedTaskRequest = {
  name: string;
  priority?: AddCountedTaskRequest.priority;
  needed?: number;
};

export namespace AddCountedTaskRequest {

  export enum priority {
    HIGH = 'HIGH',
    MEDIUM = 'MEDIUM',
    LOW = 'LOW',
    NONE = 'NONE',
  }


}
