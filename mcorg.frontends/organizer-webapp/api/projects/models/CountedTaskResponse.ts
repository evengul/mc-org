/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type CountedTaskResponse = {
  id: string;
  name: string;
  priority: CountedTaskResponse.priority;
  needed: number;
  done: number;
  category?: CountedTaskResponse.category;
};

export namespace CountedTaskResponse {

  export enum priority {
    HIGH = 'HIGH',
    MEDIUM = 'MEDIUM',
    LOW = 'LOW',
    NONE = 'NONE',
  }

  export enum category {
    BUILDING_BLOCKS = 'BUILDING_BLOCKS',
    REDSTONE = 'REDSTONE',
    NATURE = 'NATURE',
    OTHER = 'OTHER',
  }


}
