"use strict";
// Interfaz de LibManage. Los datos siempre se insertan con textContent (nunca innerHTML) para evitar XSS.
(() => {
  let token = null;
  const $ = (id) => document.getElementById(id);

  async function api(ruta, opciones = {}) {
    const cabeceras = { "Content-Type": "application/json" };
    if (token) cabeceras.Authorization = "Bearer " + token;
    const respuesta = await fetch(ruta, { ...opciones, headers: cabeceras });
    const datos = await respuesta.json().catch(() => ({}));
    if (!respuesta.ok) throw new Error(datos.error || "Error " + respuesta.status);
    return datos;
  }

  function mensaje(texto, error = false) {
    const caja = $("mensaje");
    caja.textContent = texto;
    caja.classList.toggle("error", error);
    caja.hidden = false;
  }

  function mostrarVista(nombre) {
    document.querySelectorAll(".vista").forEach((v) => { v.hidden = v.id !== nombre; });
    document.querySelectorAll(".pestanas button").forEach((b) => {
      if (b.dataset.vista === nombre) b.setAttribute("aria-current", "page"); else b.removeAttribute("aria-current");
    });
    if (nombre === "prestamos") cargarPrestamos();
    if (nombre === "recomendados") cargarRecomendados();
  }

  function itemLibro(libro, conBoton) {
    const li = document.createElement("li");
    li.className = "libro";
    li.dataset.id = libro.id;
    const titulo = document.createElement("strong");
    titulo.textContent = libro.titulo;
    const detalle = document.createElement("span");
    detalle.className = "detalle";
    detalle.textContent = `${libro.autor} · ${libro.categoria} · ${libro.anio} · ${libro.disponibles} de ${libro.ejemplares} disponibles`;
    li.append(titulo, detalle);
    if (conBoton) {
      const boton = document.createElement("button");
      boton.type = "button";
      boton.textContent = "Pedir préstamo";
      boton.disabled = libro.disponibles === 0;
      boton.addEventListener("click", () => pedir(libro.id));
      li.append(boton);
    }
    return li;
  }

  async function buscar(evento) {
    if (evento) evento.preventDefault();
    const p = new URLSearchParams();
    for (const campo of ["q", "autor", "categoria", "desde", "hasta", "orden"]) {
      const valor = $(campo).value.trim();
      if (valor) p.set(campo, valor);
    }
    if ($("disponibles").checked) p.set("disponibles", "true");
    try {
      const r = await api("/api/libros?" + p.toString());
      $("total-resultados").textContent = `${r.total} resultado${r.total === 1 ? "" : "s"}`;
      $("resultados").replaceChildren(...r.libros.map((l) => itemLibro(l, true)));
    } catch (e) { mensaje(e.message, true); }
  }

  async function pedir(libroId) {
    try {
      const p = await api("/api/prestamos", { method: "POST", body: JSON.stringify({ libroId }) });
      mensaje(`Préstamo registrado: ${p.titulo}. Devuélvelo antes del ${p.limite}.`);
      buscar();
    } catch (e) { mensaje(e.message, true); }
  }

  async function cargarPrestamos() {
    try {
      const lista = await api("/api/prestamos");
      $("tabla-prestamos").replaceChildren(...lista.map((p) => {
        const fila = document.createElement("tr");
        for (const valor of [p.titulo, p.inicio, p.limite, p.estado]) {
          const celda = document.createElement("td");
          celda.textContent = valor;
          fila.append(celda);
        }
        const accion = document.createElement("td");
        if (p.estado !== "DEVUELTO") {
          const boton = document.createElement("button");
          boton.type = "button";
          boton.textContent = "Devolver";
          boton.addEventListener("click", async () => {
            try {
              await api(`/api/prestamos/${p.id}/devolucion`, { method: "POST" });
              mensaje(`Devolución registrada: ${p.titulo}.`);
              cargarPrestamos();
            } catch (e) { mensaje(e.message, true); }
          });
          accion.append(boton);
        }
        fila.append(accion);
        return fila;
      }));
    } catch (e) { mensaje(e.message, true); }
  }

  async function cargarRecomendados() {
    try {
      const lista = await api("/api/recomendaciones?n=5");
      $("lista-recomendados").replaceChildren(...lista.map((l) => itemLibro(l, true)));
    } catch (e) { mensaje(e.message, true); }
  }

  async function ingresar(evento) {
    evento.preventDefault();
    try {
      const r = await api("/api/login", { method: "POST",
        body: JSON.stringify({ email: $("email").value, contrasena: $("contrasena").value }) });
      token = r.token;
      $("usuario-actual").textContent = `${r.nombre} (${r.rol.toLowerCase()})`;
      $("usuario-actual").hidden = false;
      $("salir").hidden = false;
      $("pestana-admin").hidden = r.rol !== "ADMIN";
      $("vista-login").hidden = true;
      $("vista-app").hidden = false;
      $("mensaje").hidden = true;
      mostrarVista("buscar");
      buscar();
    } catch (e) { mensaje(e.message, true); }
  }

  async function agregarLibro(evento) {
    evento.preventDefault();
    const cuerpo = { isbn: $("nuevo-isbn").value, titulo: $("nuevo-titulo").value, autor: $("nuevo-autor").value,
      categoria: $("nuevo-categoria").value, anio: Number($("nuevo-anio").value), ejemplares: Number($("nuevo-ejemplares").value) };
    try {
      const l = await api("/api/admin/libros", { method: "POST", body: JSON.stringify(cuerpo) });
      mensaje(`Libro agregado con ID ${l.id}: ${l.titulo}.`);
      evento.target.reset();
    } catch (e) { mensaje(e.message, true); }
  }

  async function ajustarEjemplares(evento) {
    evento.preventDefault();
    try {
      const l = await api(`/api/admin/libros/${Number($("ajuste-id").value)}/ejemplares`,
        { method: "PUT", body: JSON.stringify({ ejemplares: Number($("ajuste-total").value) }) });
      mensaje(`Ahora hay ${l.ejemplares} ejemplares de ${l.titulo}.`);
    } catch (e) { mensaje(e.message, true); }
  }

  document.addEventListener("DOMContentLoaded", () => {
    $("form-login").addEventListener("submit", ingresar);
    $("form-busqueda").addEventListener("submit", buscar);
    $("form-libro").addEventListener("submit", agregarLibro);
    $("form-ejemplares").addEventListener("submit", ajustarEjemplares);
    $("salir").addEventListener("click", () => window.location.reload());
    document.querySelectorAll(".pestanas button").forEach((b) => b.addEventListener("click", () => mostrarVista(b.dataset.vista)));
  });
})();
