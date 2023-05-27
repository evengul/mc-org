/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type ResourceResponse = {
  name: string;
  type: ResourceResponse.type;
  url: string;
};

export namespace ResourceResponse {

  export enum type {
    MOD = 'MOD',
    RESOURCE_PACK = 'RESOURCE_PACK',
    DATA_PACK = 'DATA_PACK',
  }


}
