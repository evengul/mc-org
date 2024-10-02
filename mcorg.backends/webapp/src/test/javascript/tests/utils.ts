type ENVIRONMENT = "LOCAL" | "PRODUCTION"

const getCurrentEnvironment = (): ENVIRONMENT => "LOCAL"

const urls: Record<ENVIRONMENT, string> = {
  LOCAL: "http://localhost:8080",
  PRODUCTION: "https://mcorg.app"
}

const pages = {
  MAIN: "/",
  SIGN_IN: "/auth/sign-in"
}

export const getUrl = (page: keyof typeof pages) => urls[getCurrentEnvironment()] + pages[page]





