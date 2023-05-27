/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type ErrorMessageResponseString = {
  errorType: ErrorMessageResponseString.errorType;
  message: string;
};

export namespace ErrorMessageResponseString {

  export enum errorType {
    VALIDATION = 'VALIDATION',
    ARGUMENT_MISSING = 'ARGUMENT_MISSING',
    INVALID_ARGUMENT = 'INVALID_ARGUMENT',
    NOT_FOUND = 'NOT_FOUND',
    ARCHIVED_PROJECT = 'ARCHIVED_PROJECT',
    UNKNOWN = 'UNKNOWN',
  }


}
