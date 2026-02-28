window.addEventListener('load', () => {
    const menuButton = document.getElementById("menu-button")
    const closeButton = document.getElementById("menu-close-button")
    const menu = document.getElementById("menu-links")

    menuButton.addEventListener("click", () => {
        menu.classList.remove("menu-invisible")
        menu.classList.add("menu-visible")
    })

    closeButton.addEventListener("click", () => {
        menu.classList.remove("menu-visible")
        menu.classList.add("menu-invisible")
    })
})



