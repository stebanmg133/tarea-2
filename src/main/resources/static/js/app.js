/* =========================================================================
   pedidos-restaurante - cliente
   - Cliente HTTP con token CSRF y manejo del bloqueo temporal.
   - Inicializacion por pagina segun  <body data-page="...">
   ========================================================================= */
(function () {
    "use strict";

    const state = {
        csrf: null,
        admin: false,
        mustChangePassword: false,
        locked: false,
        printMode: null,
        settings: { currencySymbol: "$", businessName: "Restaurante" }
    };

    // ---------------------------------------------------------------- utilidades
    function el(id) { return document.getElementById(id); }
    function qs(sel, root) { return (root || document).querySelector(sel); }
    function qsa(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }

    function escapeHtml(s) {
        if (s === null || s === undefined) return "";
        return String(s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
    }

    function money(v) {
        const n = Number(v || 0);
        return state.settings.currencySymbol + " " + n.toLocaleString("es-CO", { maximumFractionDigits: 0 });
    }

    function fmtDate(iso) {
        if (!iso) return "—";
        const d = new Date(iso);
        if (isNaN(d)) return iso;
        return d.toLocaleString("es-CO", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
    }

    function fmtTime(iso) {
        if (!iso) return "—";
        const d = new Date(iso);
        if (isNaN(d)) return iso;
        return d.toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit" });
    }

    let toastTimer = null;
    function toast(msg, kind) {
        let t = el("toast");
        if (!t) { t = document.createElement("div"); t.id = "toast"; document.body.appendChild(t); }
        t.className = kind || "";
        t.textContent = msg;
        t.classList.remove("hidden");
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => t.classList.add("hidden"), 3200);
    }

    function uuid() {
        if (crypto && crypto.randomUUID) return crypto.randomUUID();
        return "tok-" + Date.now() + "-" + Math.random().toString(16).slice(2);
    }

    // ---------------------------------------------------------------- overlay bloqueo
    function ensureLockOverlay() {
        if (el("lockOverlay")) return;
        const div = document.createElement("div");
        div.id = "lockOverlay";
        div.className = "lock-overlay hidden";
        div.innerHTML =
            '<div class="lock-card">' +
            '<div class="lock-ico">&#128274;</div>' +
            '<h2>Página bloqueada, ingrese contraseña</h2>' +
            '<p class="muted">El período de validación venció. Ingrese la contraseña para continuar.</p>' +
            '<input type="password" id="lockPassword" placeholder="Contraseña" autocomplete="current-password"/>' +
            '<div class="row" style="margin-top:.6rem">' +
            '<button class="btn primary block" id="lockBtn">Desbloquear</button></div>' +
            '<p id="lockError" class="error hidden"></p>' +
            '</div>';
        document.body.appendChild(div);
        el("lockBtn").addEventListener("click", doUnlock);
        el("lockPassword").addEventListener("keydown", e => { if (e.key === "Enter") doUnlock(); });
    }

    async function doUnlock() {
        const pwd = el("lockPassword").value;
        el("lockError").classList.add("hidden");
        try {
            await api("POST", "/api/lock/unlock", { password: pwd });
            state.locked = false;
            el("lockOverlay").classList.add("hidden");
            toast("Sistema desbloqueado", "ok");
            setTimeout(() => location.reload(), 500);
        } catch (e) {
            el("lockError").textContent = e.message || "No fue posible desbloquear.";
            el("lockError").classList.remove("hidden");
        }
    }

    function showLock(message) {
        ensureLockOverlay();
        state.locked = true;
        el("lockOverlay").classList.remove("hidden");
        const badge = el("lockBadge");
        if (badge) badge.classList.remove("hidden");
        if (message && el("lockError")) { el("lockError").textContent = message; el("lockError").classList.remove("hidden"); }
    }

    // ---------------------------------------------------------------- cliente HTTP
    async function api(method, url, body) {
        const headers = { "Accept": "application/json" };
        if (method !== "GET" && method !== "HEAD") {
            headers["Content-Type"] = "application/json";
            if (state.csrf) headers["X-XSRF-TOKEN"] = state.csrf;
        }
        const opts = { method, headers, credentials: "same-origin" };
        if (body !== undefined && body !== null) opts.body = JSON.stringify(body);
        let res;
        try {
            res = await fetch(url, opts);
        } catch (netErr) {
            throw new Error("No hay conexión con el servidor. Verifique la red y reintente.");
        }
        const text = await res.text();
        let data = null;
        if (text) { try { data = JSON.parse(text); } catch (e) { data = null; } }
        if (res.status === 423) {
            showLock(data && data.message ? data.message : "Página bloqueada, ingrese contraseña");
            throw new Error("Sistema bloqueado.");
        }
        if (res.status === 401) {
            const err = new Error((data && data.message) || "No autorizado.");
            err.status = 401;
            throw err;
        }
        if (!res.ok) {
            throw new Error((data && data.message) ? data.message : ("Error " + res.status));
        }
        return data;
    }

    // ---------------------------------------------------------------- inicializacion
    async function boot() {
        ensureLockOverlay();
        try {
            const session = await api("GET", "/api/session");
            state.csrf = session.csrfToken;
            state.admin = session.authenticated;
            state.mustChangePassword = session.mustChangePassword;
            state.locked = session.locked;
        } catch (e) { /* se reintenta al operar */ }

        try {
            const lock = await api("GET", "/api/lock/status");
            state.locked = lock.locked;
            if (lock.locked) showLock();
        } catch (e) { /* ignorar */ }

        try {
            const cfg = await api("GET", "/api/print/config");
            state.printMode = cfg.mode;
            const b = el("printModeBadge");
            if (b) {
                b.classList.remove("hidden");
                b.classList.add(cfg.mode === "REAL" ? "badge-real" : "badge-sim");
                b.textContent = cfg.mode === "REAL" ? "Impresión: REAL" : "Impresión: SIMULADA";
                b.title = cfg.realModeAvailable
                    ? "Modo real: envía a la impresora del servidor."
                    : "Modo simulación: NO imprime en papel, guarda la comanda en disco.";
            }
        } catch (e) { /* ignorar */ }

        try {
            const s = await api("GET", "/api/admin/settings");
            state.settings.businessName = s.businessName || state.settings.businessName;
            state.settings.currencySymbol = s.currencySymbol || state.settings.currencySymbol;
        } catch (e) { /* requiere admin; usar valores por defecto */ }

        startClock();
        pollLock();
        dispatch();
    }

    function startClock() {
        const node = el("serverTime");
        if (!node) return;
        const tick = () => { node.textContent = new Date().toLocaleTimeString("es-CO"); };
        tick();
        setInterval(tick, 15000);
    }

    function pollLock() {
        setInterval(async () => {
            try {
                const lock = await api("GET", "/api/lock/status");
                if (lock.locked && !state.locked) showLock();
            } catch (e) { /* ignorar */ }
        }, 30000);
    }

    function dispatch() {
        const page = document.body.getAttribute("data-page");
        const handlers = {
            panel: initPanel,
            mesas: initMesas,
            pedido: initPedido,
            areas: initAreas,
            pedidos: initPedidos,
            catalogo: initCatalogo,
            admin: initAdmin,
            impresion: initImpresion,
            sistema: initSistema
        };
        const fn = handlers[page];
        if (fn) { try { fn(); } catch (e) { console.error(e); toast("Error al inicializar la página", "error"); } }
    }

    // =====================================================================
    //  PANEL
    // =====================================================================
    async function initPanel() {
        const box = el("panelCards");
        const table = () => {
            const d = box.dataset;
            box.innerHTML =
                card("Pedidos activos", d.active, "") +
                card("Pendientes", d.pending, "accent") +
                card("En preparación", d.prep, "warn") +
                card("Listos", d.ready, "") +
                card("Entregados hoy", d.delivered, "ok") +
                card("Cancelados hoy", d.cancelled, "err") +
                card("Mesas ocupadas", d.occupied, "warn") +
                card("Mesas libres", d.free, "ok") +
                card("Domicilios activos", d.deliveries, "") +
                card("Impresiones pendientes", d.printPending, "warn") +
                card("Errores de impresión", d.printErrors, "err") +
                card("Comandas por reimprimir", d.needsReprint, "warn");
        };
        function card(label, value, cls) {
            return '<div class="card ' + (cls || "") + '"><div class="value">' + escapeHtml(value) + '</div><div class="label">' + escapeHtml(label) + '</div></div>';
        }
        async function load() {
            try {
                const d = await api("GET", "/api/dashboard");
                box.dataset.active = d.activeOrders; box.dataset.pending = d.pendingOrders;
                box.dataset.prep = d.inPreparationOrders; box.dataset.ready = d.readyOrders;
                box.dataset.delivered = d.deliveredToday; box.dataset.cancelled = d.cancelledToday;
                box.dataset.occupied = d.occupiedTables; box.dataset.free = d.freeTables;
                box.dataset.deliveries = d.activeDeliveries; box.dataset.printPending = d.printPending;
                box.dataset.printErrors = d.printErrors; box.dataset.needsReprint = d.needsReprint;
                table();
                const extra = el("panelExtra");
                if (extra) {
                    extra.innerHTML =
                        '<div class="card"><div class="label">Ventas del día (entregados)</div><div class="value">' + money(d.salesToday) + '</div></div>' +
                        '<div class="card ' + (d.dbOk ? "ok" : "err") + '"><div class="label">Base de datos</div><div class="value">' + (d.dbOk ? "OK" : "Error") + '</div></div>' +
                        '<div class="card"><div class="label">Última copia</div><div class="value" style="font-size:1rem">' + escapeHtml(d.lastBackupAt ? fmtDate(d.lastBackupAt) : "Nunca") + '</div><div class="label">' + escapeHtml(d.lastBackupStatus || "") + '</div></div>';
                }
            } catch (e) { toast(e.message, "error"); }
        }
        load();
        setInterval(load, 15000);
    }

    // =====================================================================
    //  MESAS / nuevo pedido
    // =====================================================================
    async function initMesas() {
        const grid = el("tablesGrid");
        const typeBox = el("typeChoices");
        let type = "MESA";
        async function load() {
            try {
                const tables = await api("GET", "/api/tables");
                grid.innerHTML = tables.map(t =>
                    '<div class="table-cell ' + (t.occupied ? "occupied" : "free") + '" data-code="' + escapeHtml(t.code) + '" data-occ="' + t.occupied + '">' +
                    '<div class="code">' + escapeHtml(t.code) + '</div>' +
                    '<div class="state">' + (t.occupied ? "Ocupada" : "Libre") + '</div>' +
                    (t.currentOrderCode ? '<div class="small muted">' + escapeHtml(t.currentOrderCode) + '</div>' : '') +
                    '</div>').join("");
                qsa(".table-cell", grid).forEach(cell => cell.addEventListener("click", () => onTable(cell.dataset.code, cell.dataset.occ === "true")));
            } catch (e) { toast(e.message, "error"); }
        }
        qsa(".type-btn", typeBox).forEach(btn => btn.addEventListener("click", () => {
            qsa(".type-btn", typeBox).forEach(b => b.classList.remove("selected"));
            btn.classList.add("selected");
            type = btn.dataset.type;
            el("tableSection").classList.toggle("hidden", type !== "MESA");
            el("deliveryQuick").classList.toggle("hidden", type === "MESA");
        }));
        async function onTable(code, occupied) {
            if (occupied) {
                try {
                    const list = await api("GET", "/api/orders?table=" + encodeURIComponent(code) + "&active=true");
                    if (list && list.length) { location.href = "/pedido?id=" + list[0].id; return; }
                    toast("No se encontró el pedido activo de la mesa " + code, "error");
                } catch (e) { toast(e.message, "error"); }
            } else {
                location.href = "/pedido?type=MESA&table=" + encodeURIComponent(code);
            }
        }
        el("goDelivery").addEventListener("click", () => location.href = "/pedido?type=" + type);
        load();
        setInterval(load, 10000);
    }

    // =====================================================================
    //  TOMA / EDICION DE PEDIDO
    // =====================================================================
    async function initPedido() {
        const params = new URLSearchParams(location.search);
        const orderId = params.get("id");
        let mode = orderId ? "existing" : "new";
        let order = null;
        let cart = [];
        let products = [];
        let categories = [];
        let selectedCat = "ALL";
        let currentType = params.get("type") || "MESA";
        let currentTable = params.get("table") || "";
        const clientToken = uuid();

        // --- cargar catalogos
        try {
            products = await api("GET", "/api/catalog/products?onlyActive=true");
            categories = await api("GET", "/api/catalog/categories?onlyActive=true&quick=true");
        } catch (e) { toast(e.message, "error"); }

        // --- tipo de pedido (solo para nuevos)
        if (mode === "new") {
            renderTypeChoices();
        } else {
            el("typeRow").classList.add("hidden");
        }

        function renderTypeChoices() {
            const box = el("typeChoices");
            const defs = [["MESA", "🍽️", "Mesa"], ["DOMICILIO", "🛵", "Domicilio"], ["LLEVAR", "🛍️", "Para llevar"]];
            box.innerHTML = defs.map(([t, ico, label]) =>
                '<button class="type-btn ' + (t === currentType ? "selected" : "") + '" data-type="' + t + '"><span class="ico">' + ico + '</span>' + label + '</button>').join("");
            qsa(".type-btn", box).forEach(b => b.addEventListener("click", () => {
                currentType = b.dataset.type;
                qsa(".type-btn", box).forEach(x => x.classList.remove("selected"));
                b.classList.add("selected");
                refreshTypeFields();
            }));
            refreshTypeFields();
        }

        function refreshTypeFields() {
            el("mesaField").classList.toggle("hidden", currentType !== "MESA");
            el("domicilioFields").classList.toggle("hidden", currentType !== "DOMICILIO");
            if (currentType === "MESA") {
                const sel = el("tableSelect");
                if (!sel.dataset.loaded) {
                    api("GET", "/api/tables").then(ts => {
                        sel.innerHTML = '<option value="">Seleccione mesa…</option>' + ts.filter(t => !t.occupied || t.code === currentTable)
                            .map(t => '<option value="' + t.code + '"' + (t.code === currentTable ? " selected" : "") + '>' + t.code + (t.occupied ? " (ocupada)" : "") + '</option>').join("");
                        sel.dataset.loaded = "1";
                    }).catch(e => toast(e.message, "error"));
                }
            }
        }

        // --- categorias rapidas
        const catBox = el("quickCats");
        catBox.innerHTML = '<button class="quick-cat selected" data-cat="ALL">Todo</button>' +
            categories.map(c => '<button class="quick-cat" data-cat="' + c.id + '">' + escapeHtml(c.name) + '</button>').join("");
        qsa(".quick-cat", catBox).forEach(b => b.addEventListener("click", () => {
            selectedCat = b.dataset.cat;
            qsa(".quick-cat", catBox).forEach(x => x.classList.remove("selected"));
            b.classList.add("selected");
            renderProducts();
        }));

        function renderProducts() {
            const grid = el("productsGrid");
            const list = products.filter(p => selectedCat === "ALL" || String(p.categoryId) === selectedCat);
            if (!list.length) { grid.innerHTML = '<p class="muted">No hay productos en esta categoría.</p>'; return; }
            grid.innerHTML = list.map(p =>
                '<div class="product-card" data-id="' + p.id + '">' +
                '<div class="pname">' + escapeHtml(p.name) + '</div>' +
                '<div class="pcat">' + escapeHtml(p.categoryName || "") + '</div>' +
                '<div class="pprice">' + money(p.basePrice) + '</div>' +
                '<div class="ptags">' + (p.hasVariants ? "Tamaños · " : "") + (p.hasFlavors ? (p.requiresFlavor ? "Sabor obligatorio" : "Sabores") : "") + '</div>' +
                '</div>').join("");
            qsa(".product-card", grid).forEach(c => c.addEventListener("click", () => chooseProduct(products.find(p => String(p.id) === c.dataset.id))));
        }
        renderProducts();

        function chooseProduct(product) {
            if (!product) return;
            const overlay = document.createElement("div");
            overlay.className = "modal-overlay";
            overlay.innerHTML =
                '<div class="modal">' +
                '<h3>' + escapeHtml(product.name) + '</h3>' +
                (product.hasVariants ? '<label>Tamaño / variante *</label><select id="mVariant">' + product.variants.filter(v => v.active).map(v => '<option value="' + escapeHtml(v.name) + '">' + escapeHtml(v.name) + ' — ' + money(v.price) + '</option>').join("") + '</select>' : '') +
                (product.hasFlavors ? '<label>Sabor' + (product.requiresFlavor ? ' *' : ' (opcional)') + '</label><select id="mFlavor"><option value="">' + (product.requiresFlavor ? 'Seleccione…' : '(sin sabor)') + '</option>' + product.flavors.map(f => '<option value="' + escapeHtml(f.name) + '">' + escapeHtml(f.name) + (Number(f.priceDelta) ? ' (+' + money(f.priceDelta) + ')' : '') + '</option>').join("") + '</select>' : '') +
                '<label>Cantidad</label><input type="number" id="mQty" value="1" min="1"/>' +
                '<label>Observación del artículo</label><textarea id="mNotes" placeholder="Ej: sin cebolla"></textarea>' +
                '<div class="row" style="margin-top:.8rem"><button class="btn primary grow" id="mAdd">Agregar al pedido</button><button class="btn" id="mCancel">Cancelar</button></div>' +
                '</div>';
            document.body.appendChild(overlay);
            overlay.addEventListener("click", e => { if (e.target === overlay) overlay.remove(); });
            el("mCancel").addEventListener("click", () => overlay.remove());
            el("mAdd").addEventListener("click", () => {
                const qty = parseInt(el("mQty").value || "1", 10);
                if (qty < 1) { toast("Cantidad inválida", "error"); return; }
                const variant = el("mVariant") ? el("mVariant").value : null;
                const flavor = el("mFlavor") ? el("mFlavor").value : null;
                if (product.hasVariants && !variant) { toast("Seleccione un tamaño", "error"); return; }
                if (product.requiresFlavor && !flavor) { toast("Seleccione un sabor", "error"); return; }
                const item = { productId: product.id, quantity: qty, variant, flavor, notes: el("mNotes").value.trim() || null };
                overlay.remove();
                if (mode === "new") { addToCart(product, item); }
                else { addItemToServer(item); }
            });
        }

        function priceFor(product, variant, flavor) {
            let price = Number(product.basePrice || 0);
            if (variant && product.variants) {
                const v = product.variants.find(x => x.name === variant);
                if (v) price = Number(v.price);
            }
            if (flavor && product.flavors) {
                const f = product.flavors.find(x => x.name === flavor);
                if (f) price += Number(f.priceDelta || 0);
            }
            return price;
        }

        // --- carrito (modo nuevo)
        function addToCart(product, item) {
            item.name = product.name;
            item.categoryName = product.categoryName;
            item.price = priceFor(product, item.variant, item.flavor);
            cart.push(item);
            renderCart();
        }

        function renderCart() {
            const box = el("cartItems");
            if (mode === "existing" && order) {
                const items = order.items.filter(i => !i.cancelled);
                if (!items.length) { box.innerHTML = '<p class="muted">Sin artículos.</p>'; }
                else {
                    box.innerHTML = items.map(i =>
                        '<div class="cart-item" data-item="' + i.id + '">' +
                        '<div class="ci-head"><span class="ci-name">' + i.quantity + '× ' + escapeHtml(i.productName) + '</span>' +
                        '<span class="right">' + money(i.lineTotal) + '</span></div>' +
                        '<div class="ci-sub">' + [i.variantName, i.flavorName].filter(Boolean).map(escapeHtml).join(" / ") + '</div>' +
                        (i.notes ? '<div class="ci-sub">📝 ' + escapeHtml(i.notes) + '</div>' : '') +
                        '<div class="ci-sub">' + i.areaNames.map(a => '<span class="pill" data-status="' + i.status + '">' + escapeHtml(a) + '</span>').join(" ") + '</div>' +
                        '<div class="row" style="margin-top:.3rem">' +
                        '<span class="qty"><button data-act="dec" data-item="' + i.id + '">−</button><span>' + i.quantity + '</span><button data-act="inc" data-item="' + i.id + '">+</button></span>' +
                        '<button class="btn small" data-act="note" data-item="' + i.id + '">Observación</button>' +
                        '<button class="btn small" data-act="del" data-item="' + i.id + '">Retirar</button>' +
                        '</div></div>').join("");
                }
            } else {
                if (!cart.length) { box.innerHTML = '<p class="muted">Agregue productos al pedido.</p>'; }
                else {
                    box.innerHTML = cart.map((i, idx) =>
                        '<div class="cart-item">' +
                        '<div class="ci-head"><span class="ci-name">' + i.quantity + '× ' + escapeHtml(i.name) + '</span><span class="right">' + money(i.price * i.quantity) + '</span></div>' +
                        '<div class="ci-sub">' + [i.variant, i.flavor].filter(Boolean).map(escapeHtml).join(" / ") + '</div>' +
                        (i.notes ? '<div class="ci-sub">📝 ' + escapeHtml(i.notes) + '</div>' : '') +
                        '<div class="row" style="margin-top:.3rem"><span class="qty"><button data-new-dec="' + idx + '">−</button><span>' + i.quantity + '</span><button data-new-inc="' + idx + '">+</button></span>' +
                        '<button class="btn small" data-new-del="' + idx + '">Quitar</button></div>' +
                        '</div>').join("");
                }
            }
            renderTotals();
            bindCartEvents();
        }

        function bindCartEvents() {
            qsa("[data-new-inc]").forEach(b => b.onclick = () => { cart[+b.dataset.newInc].quantity++; renderCart(); });
            qsa("[data-new-dec]").forEach(b => b.onclick = () => { const i = +b.dataset.newDec; if (cart[i].quantity > 1) cart[i].quantity--; else cart.splice(i, 1); renderCart(); });
            qsa("[data-new-del]").forEach(b => b.onclick = () => { cart.splice(+b.dataset.newDel, 1); renderCart(); });
            qsa("[data-act]").forEach(b => b.onclick = () => {
                const id = b.dataset.item, act = b.dataset.act;
                const item = order.items.find(x => String(x.id) === String(id));
                if (act === "inc") changeItem(id, item.quantity + 1);
                else if (act === "dec") { if (item.quantity > 1) changeItem(id, item.quantity - 1); else removeItem(id); }
                else if (act === "del") removeItem(id);
                else if (act === "note") {
                    const n = prompt("Observación para " + item.productName + ":", item.notes || "");
                    if (n !== null) changeItem(id, item.quantity, n);
                }
            });
        }

        function renderTotals() {
            let subtotal = 0;
            if (mode === "existing" && order) {
                subtotal = order.items.filter(i => !i.cancelled).reduce((s, i) => s + Number(i.lineTotal), 0);
            } else {
                subtotal = cart.reduce((s, i) => s + i.price * i.quantity, 0);
            }
            const deliveryFee = (mode === "existing" ? (order && order.type === "DOMICILIO" ? Number(order.deliveryFee) : 0)
                : (currentType === "DOMICILIO" ? Number(el("deliveryFee") && el("deliveryFee").value || 0) : 0));
            el("subtotal").textContent = money(subtotal);
            el("deliveryRow").classList.toggle("hidden", (mode === "existing" ? order.type : currentType) !== "DOMICILIO");
            el("deliveryShown").textContent = money(deliveryFee);
            el("total").textContent = money(subtotal + deliveryFee);
        }
        if (el("deliveryFee")) el("deliveryFee").addEventListener("input", renderTotals);

        // --- acciones de servidor en modo existente
        async function addItemToServer(item) {
            try { order = await api("POST", "/api/orders/" + order.id + "/items", item); refreshOrderView(); toast("Artículo agregado", "ok"); }
            catch (e) { toast(e.message, "error"); }
        }
        async function changeItem(itemId, quantity, notes) {
            try {
                order = await api("PUT", "/api/orders/" + order.id + "/items/" + itemId, { quantity, notes: notes !== undefined ? notes : undefined });
                refreshOrderView();
            } catch (e) { toast(e.message, "error"); }
        }
        async function removeItem(itemId) {
            if (!confirm("¿Retirar este artículo del pedido?")) return;
            try { order = await api("DELETE", "/api/orders/" + order.id + "/items/" + itemId); refreshOrderView(); toast("Artículo retirado", "ok"); }
            catch (e) { toast(e.message, "error"); }
        }

        // --- cargar pedido existente
        if (mode === "existing") {
            try {
                order = await api("GET", "/api/orders/" + orderId);
                currentType = order.type;
                fillOrderFields();
                refreshOrderView();
                loadPrintJobs();
            } catch (e) { toast(e.message, "error"); }
        }

        function fillOrderFields() {
            el("orderTitle").textContent = "Pedido " + order.code + " — " + order.typeLabel;
            el("customerName").value = order.customerName || "";
            el("customerPhone").value = order.customerPhone || "";
            if (el("deliveryAddress")) el("deliveryAddress").value = order.deliveryAddress || "";
            if (el("deliveryFee")) el("deliveryFee").value = order.deliveryFee != null ? order.deliveryFee : "";
            el("generalNotes").value = order.generalNotes || "";
            el("typeRow").classList.add("hidden");
            el("orderMeta").innerHTML =
                '<span class="pill" data-status="' + order.status + '">' + escapeHtml(order.statusLabel) + '</span> ' +
                (order.tableCode ? '<strong>Mesa ' + escapeHtml(order.tableCode) + '</strong> ' : '') +
                (order.needsReprint ? '<span class="badge badge-lock">Comanda requiere actualización</span>' : '');
        }

        function refreshOrderView() {
            if (!order) return;
            fillOrderFields();
            renderCart();
            el("cancelOrderBtn").classList.toggle("hidden", !order.active);
            el("addItemsPanel").classList.remove("hidden");
            el("confirmNewBtn").classList.add("hidden");
            loadPrintJobs();
        }

        // --- confirmar pedido nuevo
        el("confirmNewBtn").addEventListener("click", async () => {
            if (!cart.length) { toast("Agregue al menos un artículo", "error"); return; }
            const payload = {
                type: currentType,
                tableCode: currentType === "MESA" ? (el("tableSelect") ? el("tableSelect").value : currentTable) : null,
                customerName: el("customerName").value.trim() || null,
                customerPhone: el("customerPhone").value.trim() || null,
                deliveryAddress: currentType === "DOMICILIO" ? el("deliveryAddress").value.trim() : null,
                deliveryFee: currentType === "DOMICILIO" ? Number(el("deliveryFee").value || 0) : null,
                generalNotes: el("generalNotes").value.trim() || null,
                items: cart.map(i => ({ productId: i.productId, quantity: i.quantity, variant: i.variant, flavor: i.flavor, notes: i.notes })),
                clientToken: clientToken
            };
            if (currentType === "MESA" && !payload.tableCode) { toast("Seleccione una mesa", "error"); return; }
            if (currentType === "DOMICILIO" && (!payload.customerName || !payload.customerPhone || !payload.deliveryAddress)) {
                toast("En domicilio complete nombre, teléfono y dirección", "error"); return;
            }
            const btn = el("confirmNewBtn"); btn.disabled = true;
            try {
                order = await api("POST", "/api/orders", payload);
                mode = "existing";
                history.replaceState(null, "", "/pedido?id=" + order.id);
                toast("Pedido " + order.code + " creado", "ok");
                refreshOrderView();
                loadPrintJobs();
            } catch (e) { toast(e.message, "error"); }
            finally { btn.disabled = false; }
        });

        // --- guardar cambios de datos (modo existente)
        el("saveHeaderBtn").addEventListener("click", async () => {
            if (!order) { toast("Cree el pedido primero", "error"); return; }
            try {
                order = await api("PUT", "/api/orders/" + order.id, {
                    customerName: el("customerName").value.trim() || null,
                    customerPhone: el("customerPhone").value.trim() || null,
                    deliveryAddress: el("deliveryAddress") ? el("deliveryAddress").value.trim() : null,
                    deliveryFee: order.type === "DOMICILIO" ? Number(el("deliveryFee").value || 0) : null,
                    generalNotes: el("generalNotes").value.trim() || null
                });
                refreshOrderView();
                toast("Datos guardados", "ok");
            } catch (e) { toast(e.message, "error"); }
        });

        // --- imprimir
        el("printFullBtn").addEventListener("click", () => doPrint(order, "FULL"));
        el("printAreaBtn").addEventListener("click", () => {
            const areas = collectAreas(order);
            if (!areas.length) { toast("El pedido no tiene áreas", "error"); return; }
            const choice = prompt("Imprimir solo un área. Escriba el nombre del área:\n" + areas.map(a => "- " + a.name).join("\n"), areas[0].name);
            if (!choice) return;
            const area = areas.find(a => a.name.toLowerCase() === choice.trim().toLowerCase());
            if (!area) { toast("Área no encontrada", "error"); return; }
            doPrint(order, "AREA", area.id);
        });

        async function doPrint(o, target, areaId) {
            if (!o) { toast("Cree el pedido primero", "error"); return; }
            if (o.needsReprint && target === "FULL") {
                if (!confirm("La comanda cambió desde la última impresión. ¿Desea imprimir la versión actualizada?")) return;
            }
            try {
                const job = await api("POST", "/api/print/orders/" + o.id, { target, areaId: areaId || null, reprint: target === "FULL" && o.needsReprint ? false : undefined });
                toast("Impresión: " + job.statusLabel + (job.simulated ? " (simulada: no sale papel)" : ""), job.status === "ACEPTADO" ? "ok" : "error");
                loadPrintJobs();
                const refreshed = await api("GET", "/api/orders/" + o.id);
                order = refreshed; fillOrderFields();
            } catch (e) { toast(e.message, "error"); }
        }

        function collectAreas(o) {
            const map = {};
            o.items.filter(i => !i.cancelled).forEach(i => i.tasks.forEach(t => { map[t.areaId] = { id: t.areaId, name: t.areaName }; }));
            return Object.values(map);
        }

        async function loadPrintJobs() {
            if (!order) return;
            try {
                const jobs = await api("GET", "/api/print/orders/" + order.id + "/jobs");
                const box = el("printJobs");
                if (!jobs.length) { box.innerHTML = '<p class="muted small">Sin impresiones registradas.</p>'; return; }
                box.innerHTML = jobs.map(j =>
                    '<div class="small" style="margin:.2rem 0">' +
                    '<span class="pill" style="background:' + j.statusColor + '">' + escapeHtml(j.statusLabel) + '</span> ' +
                    (j.areaName ? escapeHtml(j.areaName) + ' · ' : 'Completo · ') +
                    'v' + j.sourceVersion + ' · intento ' + j.attempt + (j.simulated ? ' · SIMULADO' : '') +
                    '<div class="muted">' + escapeHtml(j.message || "") + '</div>' +
                    '</div>').join("");
            } catch (e) { /* ignorar */ }
        }

        // --- cancelar
        el("cancelOrderBtn").addEventListener("click", async () => {
            if (!order) return;
            if (!confirm("¿Cancelar el pedido " + order.code + "? Se conservará en el historial y no contará en ventas.")) return;
            const reason = prompt("Motivo de la cancelación:", "Cancelado por el cliente");
            if (reason === null) return;
            try {
                order = await api("POST", "/api/orders/" + order.id + "/cancel", { reason });
                toast("Pedido cancelado", "ok");
                refreshOrderView();
            } catch (e) { toast(e.message, "error"); }
        });

        // --- estados rapidos por artículo (modo existente)
        el("markReadyBtn") && el("markReadyBtn").addEventListener("click", async () => {
            if (!order) return;
            try {
                for (const i of order.items.filter(x => !x.cancelled && x.status !== "ENTREGADO")) {
                    order = await api("POST", "/api/orders/" + order.id + "/items/" + i.id + "/status", { status: "LISTO" });
                }
                refreshOrderView();
                toast("Artículos marcados como listos", "ok");
            } catch (e) { toast(e.message, "error"); }
        });
    }

    // =====================================================================
    //  AREAS DE PREPARACION
    // =====================================================================
    async function initAreas() {
        let areas = [];
        let selected = null;
        try { areas = await api("GET", "/api/catalog/areas?onlyActive=false"); } catch (e) { toast(e.message, "error"); }
        const tabs = el("areaTabs");
        tabs.innerHTML = areas.filter(a => a.active).map(a =>
            '<button class="area-tab" data-id="' + a.id + '">' + escapeHtml(a.name) + '</button>').join("");
        qsa(".area-tab", tabs).forEach(b => b.addEventListener("click", () => {
            selected = b.dataset.id;
            qsa(".area-tab", tabs).forEach(x => x.classList.remove("selected"));
            b.classList.add("selected");
            load();
        }));
        if (areas.length) {
            const first = qsa(".area-tab", tabs)[0];
            if (first) { first.click(); }
        }

        async function load() {
            if (!selected) return;
            try {
                const items = await api("GET", "/api/areas/" + selected + "/work");
                const grid = el("workGrid");
                if (!items.length) { grid.innerHTML = '<p class="muted">No hay trabajo pendiente en esta área.</p>'; return; }
                grid.innerHTML = items.map(i =>
                    '<div class="work-card" data-order="' + i.orderId + '" data-item="' + i.taskId + '">' +
                    '<div class="wc-top"><span class="wc-order">' + escapeHtml(i.orderCode) + '</span>' +
                    '<span class="type-tag" data-type="' + i.type + '">' + escapeHtml(i.typeLabel) + '</span></div>' +
                    '<div class="wc-prod">' + i.quantity + '× ' + escapeHtml(i.productName) + '</div>' +
                    '<div class="wc-meta">' + [i.variantName, i.flavorName].filter(Boolean).map(escapeHtml).join(" / ") + '</div>' +
                    (i.tableCode ? '<div class="wc-meta">Mesa ' + escapeHtml(i.tableCode) + '</div>' : '') +
                    (i.customerName ? '<div class="wc-meta">Cliente: ' + escapeHtml(i.customerName) + '</div>' : '') +
                    (i.itemNotes ? '<div class="wc-meta">📝 ' + escapeHtml(i.itemNotes) + '</div>' : '') +
                    (i.generalNotes ? '<div class="wc-meta">🗒️ ' + escapeHtml(i.generalNotes) + '</div>' : '') +
                    '<div class="row" style="margin-top:.5rem">' +
                    '<button class="btn small warn" data-act="prep">En preparación</button>' +
                    '<button class="btn small ok" data-act="ready">Listo</button>' +
                    '<button class="btn small blue" data-act="delivered">Entregado</button>' +
                    '<button class="btn small" data-act="printarea">Imprimir área</button>' +
                    '</div></div>').join("");
                qsa(".work-card", grid).forEach(card => {
                    const orderId = card.dataset.order;
                    const itemId = card.dataset.item;
                    qsa("[data-act]", card).forEach(btn => btn.addEventListener("click", async () => {
                        const act = btn.dataset.act;
                        try {
                            if (act === "printarea") {
                                const job = await api("POST", "/api/print/orders/" + orderId, { target: "AREA", areaId: Number(selected) });
                                toast("Impresión área: " + job.statusLabel + (job.simulated ? " (simulada)" : ""), job.status === "ACEPTADO" ? "ok" : "error");
                                return;
                            }
                            const status = act === "prep" ? "EN_PREPARACION" : act === "ready" ? "LISTO" : "ENTREGADO";
                            await api("POST", "/api/orders/" + orderId + "/items/" + itemId + "/status", { status, areaId: Number(selected) });
                            toast("Actualizado", "ok");
                            load();
                        } catch (e) { toast(e.message, "error"); }
                    }));
                });
            } catch (e) { toast(e.message, "error"); }
        }
        setInterval(load, 8000);
    }

    // =====================================================================
    //  RESUMEN E HISTORIAL
    // =====================================================================
    async function initPedidos() {
        const form = el("filters");
        async function load() {
            const p = new URLSearchParams();
            ["code", "type", "status", "table"].forEach(k => { const v = el("f_" + k).value.trim(); if (v) p.set(k, v); });
            if (el("f_active").checked) p.set("active", "true");
            if (el("f_from").value) p.set("from", el("f_from").value);
            if (el("f_to").value) p.set("to", el("f_to").value);
            try {
                const list = await api("GET", "/api/orders?" + p.toString());
                const body = el("ordersBody");
                if (!list.length) { body.innerHTML = '<tr><td colspan="8" class="muted">Sin resultados.</td></tr>'; return; }
                body.innerHTML = list.map(o =>
                    '<tr class="order-row" data-id="' + o.id + '">' +
                    '<td><strong>' + escapeHtml(o.code) + '</strong></td>' +
                    '<td><span class="type-tag" data-type="' + o.type + '">' + escapeHtml(o.typeLabel) + '</span></td>' +
                    '<td>' + escapeHtml(o.tableCode || o.deliveryAddress || o.customerName || "—") + '</td>' +
                    '<td><span class="pill" data-status="' + o.status + '">' + escapeHtml(o.statusLabel) + '</span>' + (o.needsReprint ? ' <span class="badge badge-lock">reimprimir</span>' : '') + '</td>' +
                    '<td>' + o.itemCount + '</td>' +
                    '<td>' + money(o.total) + '</td>' +
                    '<td class="small">' + fmtDate(o.createdAt) + '</td>' +
                    '<td><button class="btn small" data-open="' + o.id + '">Ver</button></td>' +
                    '</tr>').join("");
                qsa("[data-open]", body).forEach(b => b.addEventListener("click", () => location.href = "/pedido?id=" + b.dataset.open));
                qsa(".order-row", body).forEach(r => r.addEventListener("click", e => { if (e.target.tagName !== "BUTTON") location.href = "/pedido?id=" + r.dataset.id; }));
            } catch (e) { toast(e.message, "error"); }
        }
        form.addEventListener("submit", e => { e.preventDefault(); load(); });
        el("clearFilters").addEventListener("click", () => { form.reset(); load(); });
        load();
    }

    // =====================================================================
    //  CATALOGO
    // =====================================================================
    async function initCatalogo() {
        let areas = [];
        let categories = [];
        try {
            areas = await api("GET", "/api/catalog/areas?onlyActive=false");
            categories = await api("GET", "/api/catalog/categories?onlyActive=false");
        } catch (e) { /* puede requerir admin */ }

        async function load() {
            try {
                const products = await api("GET", "/api/admin/catalog/products?onlyActive=false");
                const body = el("catalogBody");
                body.innerHTML = products.map(p =>
                    '<tr><td>' + escapeHtml(p.name) + '</td>' +
                    '<td>' + escapeHtml(p.categoryName || "—") + '</td>' +
                    '<td>' + money(p.basePrice) + '</td>' +
                    '<td class="small">' + (p.areaNames || []).map(escapeHtml).join(", ") + '</td>' +
                    '<td class="small">' + (p.hasVariants ? p.variants.map(v => escapeHtml(v.name)).join(", ") : "—") + '</td>' +
                    '<td class="small">' + (p.hasFlavors ? p.flavors.map(f => escapeHtml(f.name)).join(", ") : "—") + '</td>' +
                    '<td>' + (p.active ? '<span class="pill" data-status="ENTREGADO">Activo</span>' : '<span class="pill" data-status="CANCELADO">Inactivo</span>') + '</td>' +
                    '<td><button class="btn small" data-edit="' + p.id + '">Editar</button> ' +
                    '<button class="btn small" data-toggle="' + p.id + '" data-active="' + p.active + '">' + (p.active ? "Desactivar" : "Activar") + '</button></td>' +
                    '</tr>').join("");
                qsa("[data-toggle]", body).forEach(b => b.addEventListener("click", async () => {
                    try { await api("POST", "/api/admin/catalog/products/" + b.dataset.toggle + "/active?value=" + (b.dataset.active !== "true"), null); load(); }
                    catch (e) { toast(e.message, "error"); }
                }));
                qsa("[data-edit]", body).forEach(b => b.addEventListener("click", () => openEditor(products.find(p => String(p.id) === b.dataset.edit))));
            } catch (e) { showAdminNeeded(e); }
        }

        function showAdminNeeded(e) {
            el("catalogBody").innerHTML = '<tr><td colspan="8" class="muted">Inicie sesión en Administración para gestionar el catálogo.</td></tr>';
            toast(e.message, "error");
        }

        function openEditor(product) {
            const isNew = !product;
            product = product || { id: null, name: "", categoryId: null, basePrice: 0, active: true, requiresFlavor: false, flavors: [], variants: [], areaIds: [] };
            const overlay = document.createElement("div");
            overlay.className = "modal-overlay";
            overlay.innerHTML =
                '<div class="modal">' +
                '<h3>' + (isNew ? "Nuevo producto" : "Editar producto") + '</h3>' +
                '<label>Nombre *</label><input id="pName" value="' + escapeHtml(product.name) + '"/>' +
                '<label>Categoría</label><select id="pCat"><option value="">(sin categoría)</option>' +
                categories.map(c => '<option value="' + c.id + '"' + (c.id === product.categoryId ? " selected" : "") + '>' + escapeHtml(c.name) + '</option>').join("") + '</select>' +
                '<label>Precio base *</label><input id="pPrice" type="number" min="0" value="' + Number(product.basePrice || 0) + '"/>' +
                '<label>Descripción</label><input id="pDesc" value="' + escapeHtml(product.description || "") + '"/>' +
                '<label><input type="checkbox" id="pActive" style="width:auto"' + (product.active ? " checked" : "") + '/> Activo</label>' +
                '<label><input type="checkbox" id="pReqFlavor" style="width:auto"' + (product.requiresFlavor ? " checked" : "") + '/> El sabor es obligatorio</label>' +
                '<label>Áreas de destino</label><div id="pAreas">' + areas.map(a =>
                    '<label style="font-weight:400"><input type="checkbox" style="width:auto" value="' + a.id + '"' + (product.areaIds && product.areaIds.includes(a.id) ? " checked" : "") + '/> ' + escapeHtml(a.name) + '</label>').join("") + '</div>' +
                '<label>Sabores (uno por línea, opcional +precio con "Nombre|2000")</label><textarea id="pFlavors">' + escapeHtml((product.flavors || []).map(f => f.name + (Number(f.priceDelta) ? "|" + Number(f.priceDelta) : "")).join("\n")) + '</textarea>' +
                '<label>Tamaños/variantes (una por línea: "Nombre|Precio")</label><textarea id="pVariants">' + escapeHtml((product.variants || []).map(v => v.name + "|" + Number(v.price)).join("\n")) + '</textarea>' +
                '<div class="row" style="margin-top:.8rem"><button class="btn primary grow" id="pSave">Guardar</button><button class="btn" id="pCancel">Cancelar</button></div>' +
                '<p id="pErr" class="error hidden"></p>' +
                '</div>';
            document.body.appendChild(overlay);
            el("pCancel").addEventListener("click", () => overlay.remove());
            el("pSave").addEventListener("click", async () => {
                const parseLines = (txt, numeric) => txt.split("\n").map(l => l.trim()).filter(Boolean).map(l => {
                    const [n, v] = l.split("|");
                    return numeric ? { name: n.trim(), price: Number(v || 0) } : { name: n.trim(), priceDelta: Number(v || 0) };
                });
                const payload = {
                    name: el("pName").value.trim(),
                    categoryId: el("pCat").value ? Number(el("pCat").value) : null,
                    basePrice: Number(el("pPrice").value || 0),
                    active: el("pActive").checked,
                    requiresFlavor: el("pReqFlavor").checked,
                    description: el("pDesc").value.trim() || null,
                    areaIds: qsa("#pAreas input:checked").map(i => Number(i.value)),
                    flavors: parseLines(el("pFlavors").value, false),
                    variants: parseLines(el("pVariants").value, true)
                };
                if (!payload.name) { el("pErr").textContent = "El nombre es obligatorio"; el("pErr").classList.remove("hidden"); return; }
                try {
                    if (isNew) await api("POST", "/api/admin/catalog/products", payload);
                    else await api("PUT", "/api/admin/catalog/products/" + product.id, payload);
                    overlay.remove();
                    toast("Producto guardado", "ok");
                    load();
                } catch (e) { el("pErr").textContent = e.message; el("pErr").classList.remove("hidden"); }
            });
        }

        el("newProductBtn").addEventListener("click", () => openEditor(null));
        load();
    }

    // =====================================================================
    //  ADMINISTRACION
    // =====================================================================
    async function initAdmin() {
        const loginBox = el("adminLogin");
        const panel = el("adminPanel");

        async function refresh() {
            try {
                const s = await api("GET", "/api/admin/session");
                state.admin = s.authenticated;
                loginBox.classList.toggle("hidden", state.admin);
                panel.classList.toggle("hidden", !state.admin);
                if (state.admin) { loadAll(); }
            } catch (e) { toast(e.message, "error"); }
        }

        el("loginBtn").addEventListener("click", async () => {
            const pwd = el("adminPassword").value;
            try {
                await api("POST", "/api/admin/login", { password: pwd });
                el("adminPassword").value = "";
                toast("Sesión iniciada", "ok");
                refresh();
            } catch (e) { toast(e.message, "error"); }
        });
        el("adminPassword").addEventListener("keydown", e => { if (e.key === "Enter") el("loginBtn").click(); });
        el("logoutBtn").addEventListener("click", async () => {
            try { await api("POST", "/api/admin/logout", {}); toast("Sesión cerrada", "ok"); refresh(); } catch (e) { toast(e.message, "error"); }
        });

        async function loadAll() {
            await Promise.all([loadSettings(), loadLock(), loadPrint(), loadEvents(), loadBackups()]);
        }

        async function loadSettings() {
            try {
                const s = await api("GET", "/api/admin/settings");
                el("sBusiness").value = s.businessName || "";
                el("sCurrency").value = s.currencySymbol || "$";
                el("sDelivery").value = s.defaultDeliveryFee != null ? s.defaultDeliveryFee : 0;
            } catch (e) { /* */ }
        }
        el("saveSettingsBtn").addEventListener("click", async () => {
            try {
                await api("PUT", "/api/admin/settings", {
                    businessName: el("sBusiness").value.trim(),
                    currencySymbol: el("sCurrency").value.trim(),
                    defaultDeliveryFee: Number(el("sDelivery").value || 0)
                });
                toast("Configuración guardada", "ok");
            } catch (e) { toast(e.message, "error"); }
        });

        async function loadLock() {
            try {
                const l = await api("GET", "/api/admin/lock/status");
                el("lockInfo").innerHTML =
                    '<div class="card ' + (l.locked ? "err" : "ok") + '"><div class="label">Estado</div><div class="value" style="font-size:1.1rem">' + (l.locked ? "BLOQUEADO" : "Activo") + '</div></div>' +
                    '<div class="card"><div class="label">Próxima validación</div><div class="value" style="font-size:1.1rem">' + fmtDate(l.nextDueAt) + '</div></div>' +
                    '<div class="card"><div class="label">Intervalo</div><div class="value" style="font-size:1.1rem">' + l.intervalMonths + ' meses</div></div>' +
                    '<div class="card ' + (l.mustChangePassword ? "warn" : "ok") + '"><div class="label">Credencial inicial</div><div class="value" style="font-size:1.1rem">' + (l.mustChangePassword ? "Debe cambiarse" : "Cambiada") + '</div></div>';
                el("lInterval").value = l.intervalMonths;
                el("lEnabled").checked = l.moduleEnabled;
                el("lEmail").value = l.adminEmail || "";
            } catch (e) { /* */ }
        }
        el("saveLockBtn").addEventListener("click", async () => {
            try {
                await api("PUT", "/api/admin/lock/settings", {
                    moduleEnabled: el("lEnabled").checked,
                    intervalMonths: Number(el("lInterval").value || 2),
                    adminEmail: el("lEmail").value.trim()
                });
                toast("Módulo de bloqueo actualizado", "ok");
                loadLock();
            } catch (e) { toast(e.message, "error"); }
        });
        el("changePwdBtn").addEventListener("click", async () => {
            try {
                await api("POST", "/api/admin/password", {
                    currentPassword: el("cpCurrent").value,
                    newPassword: el("cpNew").value,
                    confirmPassword: el("cpConfirm").value
                });
                el("cpCurrent").value = el("cpNew").value = el("cpConfirm").value = "";
                toast("Contraseña cambiada", "ok");
                loadLock();
            } catch (e) { toast(e.message, "error"); }
        });
        el("tempPwdBtn").addEventListener("click", async () => {
            try {
                await api("POST", "/api/admin/temp-password", { password: el("tpValue").value, validHours: Number(el("tpHours").value || 24) });
                el("tpValue").value = "";
                toast("Contraseña temporal creada", "ok");
                loadLock();
            } catch (e) { toast(e.message, "error"); }
        });
        el("clearTempPwdBtn").addEventListener("click", async () => {
            try { await api("DELETE", "/api/admin/temp-password", null); toast("Contraseña temporal eliminada", "ok"); loadLock(); }
            catch (e) { toast(e.message, "error"); }
        });

        async function loadPrint() {
            try {
                const c = await api("GET", "/api/admin/print/config");
                el("printModeInfo").textContent = "Modo actual: " + c.mode + (c.mode === "REAL" ? " (envía a la impresora del servidor)" : " (simulación: no imprime en papel)");
                el("printerSelect").innerHTML = '<option value="">(impresora por defecto)</option>' +
                    (c.availablePrinters || []).map(p => '<option value="' + escapeHtml(p) + '"' + (p === c.printerName ? " selected" : "") + '>' + escapeHtml(p) + '</option>').join("");
                if (c.printerName && !(c.availablePrinters || []).includes(c.printerName)) {
                    el("printerSelect").innerHTML += '<option value="' + escapeHtml(c.printerName) + '" selected>' + escapeHtml(c.printerName) + ' (no detectada)</option>';
                }
                el("paperWidth").value = c.paperWidth;
            } catch (e) { /* */ }
        }
        el("savePrintBtn").addEventListener("click", async () => {
            try {
                await api("PUT", "/api/admin/print/config", { printerName: el("printerSelect").value, paperWidth: Number(el("paperWidth").value || 42) });
                toast("Configuración de impresión guardada", "ok");
                loadPrint();
            } catch (e) { toast(e.message, "error"); }
        });

        async function loadEvents() {
            try {
                const evs = await api("GET", "/api/admin/events");
                el("eventsBody").innerHTML = evs.map(e =>
                    '<tr><td class="small">' + fmtDate(e.createdAt) + '</td><td>' + escapeHtml(e.typeLabel) + '</td><td class="small">' + escapeHtml(e.description) + '</td><td class="small">' + escapeHtml(e.actor || "") + '</td></tr>').join("");
            } catch (e) { /* */ }
        }

        async function loadBackups() {
            try {
                const bs = await api("GET", "/api/admin/backup");
                el("backupsBody").innerHTML = bs.length ? bs.map(b =>
                    '<tr><td class="small">' + fmtDate(b.createdAt) + '</td><td>' + escapeHtml(b.fileName) + '</td><td>' + (b.sizeBytes != null ? Math.round(b.sizeBytes / 1024) + " KB" : "—") + '</td><td>' + (b.success ? "OK" : "Error") + '</td><td class="small">' + escapeHtml(b.message || "") + '</td></tr>').join("")
                    : '<tr><td colspan="5" class="muted">Sin copias registradas.</td></tr>';
            } catch (e) { /* */ }
        }
        el("backupBtn").addEventListener("click", async () => {
            try { const b = await api("POST", "/api/admin/backup", {}); toast(b.success ? "Copia creada: " + b.fileName : "Error: " + b.message, b.success ? "ok" : "error"); loadBackups(); }
            catch (e) { toast(e.message, "error"); }
        });

        refresh();
    }

    // =====================================================================
    //  IMPRESION
    // =====================================================================
    async function initImpresion() {
        async function load() {
            try {
                const jobs = await api("GET", "/api/print/jobs/recent");
                const body = el("jobsBody");
                if (!jobs.length) { body.innerHTML = '<tr><td colspan="7" class="muted">Sin trabajos de impresión.</td></tr>'; return; }
                body.innerHTML = jobs.map(j =>
                    '<tr><td>' + escapeHtml(j.orderCode) + '</td>' +
                    '<td>' + escapeHtml(j.areaName || "Completo") + '</td>' +
                    '<td><span class="pill" style="background:' + j.statusColor + '">' + escapeHtml(j.statusLabel) + '</span></td>' +
                    '<td>v' + j.sourceVersion + ' / intento ' + j.attempt + (j.reprint ? " (reimpr.)" : "") + '</td>' +
                    '<td>' + (j.simulated ? "SIMULADO" : "Real") + (j.physicalConfirmed ? " · papel OK" : "") + '</td>' +
                    '<td class="small">' + escapeHtml(j.message || "") + '</td>' +
                    '<td><button class="btn small" data-retry="' + j.id + '">Reintentar</button> <button class="btn small" data-slip="' + j.id + '">Ver comanda</button></td>' +
                    '</tr>').join("");
                qsa("[data-retry]", body).forEach(b => b.addEventListener("click", async () => {
                    try { const j = await api("POST", "/api/print/jobs/" + b.dataset.retry + "/retry", {}); toast("Reintento: " + j.statusLabel, j.status === "ACEPTADO" ? "ok" : "error"); load(); }
                    catch (e) { toast(e.message, "error"); }
                }));
                qsa("[data-slip]", body).forEach(b => b.addEventListener("click", () => {
                    const j = jobs.find(x => String(x.id) === b.dataset.slip);
                    const overlay = document.createElement("div");
                    overlay.className = "modal-overlay";
                    overlay.innerHTML = '<div class="modal"><h3>Comanda ' + escapeHtml(j.orderCode) + '</h3><pre class="slip">' + escapeHtml(j.content) + '</pre><button class="btn block" id="slipClose">Cerrar</button></div>';
                    document.body.appendChild(overlay);
                    el("slipClose").addEventListener("click", () => overlay.remove());
                }));
            } catch (e) { toast(e.message, "error"); }
        }
        load();
        setInterval(load, 10000);
    }

    // =====================================================================
    //  SISTEMA
    // =====================================================================
    async function initSistema() {
        async function load() {
            try {
                const s = await api("GET", "/api/system/status");
                el("systemBox").innerHTML =
                    '<div class="card ' + (s.dbOk ? "ok" : "err") + '"><div class="label">Base de datos</div><div class="value" style="font-size:1.1rem">' + (s.dbOk ? "OK" : "Error") + '</div><div class="label">' + escapeHtml(s.dbProduct || "") + '</div></div>' +
                    '<div class="card"><div class="label">Ubicación de datos</div><div class="value" style="font-size:.9rem;word-break:break-all">' + escapeHtml(s.dataPath || "") + '</div></div>' +
                    '<div class="card"><div class="label">Pedidos totales</div><div class="value">' + s.totalOrders + '</div></div>' +
                    '<div class="card"><div class="label">Pedidos activos</div><div class="value">' + s.activeOrders + '</div></div>' +
                    '<div class="card ' + (s.printMode === "SIMULATION" ? "warn" : "ok") + '"><div class="label">Modo de impresión</div><div class="value" style="font-size:1.1rem">' + escapeHtml(s.printMode) + '</div></div>' +
                    '<div class="card"><div class="label">Trabajos de impresión</div><div class="value">' + s.printJobsTotal + '</div><div class="label">con error: ' + s.printJobsError + '</div></div>' +
                    '<div class="card ' + (s.lockModuleEnabled ? (s.locked ? "err" : "ok") : "warn") + '"><div class="label">Bloqueo temporal</div><div class="value" style="font-size:1.1rem">' + (s.lockModuleEnabled ? (s.locked ? "BLOQUEADO" : "Activo") : "Desactivado") + '</div><div class="label">' + fmtDate(s.nextDueAt) + '</div></div>' +
                    '<div class="card"><div class="label">Última copia</div><div class="value" style="font-size:.95rem">' + escapeHtml(s.lastBackupName || "Nunca") + '</div><div class="label">' + fmtDate(s.lastBackupAt) + '</div></div>' +
                    '<div class="card"><div class="label">Hora del servidor</div><div class="value" style="font-size:1rem">' + fmtDate(s.serverTime) + '</div></div>';
            } catch (e) { toast(e.message, "error"); }
        }
        load();
        setInterval(load, 15000);
    }

    document.addEventListener("DOMContentLoaded", boot);
})();
