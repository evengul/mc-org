/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type TaskResponse = {
  id: string;
  name: string;
  priority: TaskResponse.priority;
  isDone: boolean;
};

export namespace TaskResponse {

  export enum priority {
    HIGH = 'HIGH',
    MEDIUM = 'MEDIUM',
    LOW = 'LOW',
    NONE = 'NONE',
  }


}
