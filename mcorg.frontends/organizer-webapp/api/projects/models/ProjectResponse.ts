/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { CountedTaskResponse } from './CountedTaskResponse';
import type { TaskResponse } from './TaskResponse';

export type ProjectResponse = {
  id: string;
  name: string;
  isArchived: boolean;
  tasks: Array<TaskResponse>;
  countedTasks: Array<CountedTaskResponse>;
};
