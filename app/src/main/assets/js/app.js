const route = [[14.6231495,-90.5510877,'Punto Roosevelt'],[14.6224,-90.5685,'Majadas'],[14.6094,-90.5899,'San Cristóbal'],[14.5964,-90.6130,'Cliente mayorista']];
let maps = {}, markers = {}, step = 0;
let stops = [];

const SERVIDOR_URL = 'http://10.163.121.72:3000';

async function loginApp() {
    const u = document.getElementById('u').value.trim();
    const p = document.getElementById('p').value.trim();
    const errorBox = document.getElementById('loginError');
    const loginBtn = document.querySelector('#login button');

    const textoOriginal = loginBtn.textContent;
    loginBtn.textContent = "Conectando...";

    try {
        const response = await fetch(`${SERVIDOR_URL}/api/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ usuario: u, password: p })
        });

        const data = await response.json();

        if (response.ok && data.exito) {
            localStorage.setItem('vendedor_id', data.datos.id);
            localStorage.setItem('vendedor_nombre', data.datos.nombre);

            if (window.AndroidBridge) {
                AndroidBridge.iniciarServicioRastreo(data.datos.id.toString());
            }

            document.getElementById('login').classList.add('hide');
            document.getElementById('shell').classList.remove('hide');

            const bTag = document.querySelector('.brandCompact b');
            if(bTag) bTag.textContent = data.datos.nombre;

            renderStops();
            setTimeout(() => { initMap('homeMap'); }, 260);
        } else {
            errorBox.textContent = data.mensaje || "Credenciales incorrectas";
            errorBox.style.display = 'block';
        }
    } catch (error) {
        console.error("Error de conexión:", error);
        errorBox.textContent = "Error al conectar con el servidor.";
        errorBox.style.display = 'block';
    } finally {
        loginBtn.textContent = textoOriginal;
    }
}

function logoutApp() {
    document.getElementById('shell').classList.add('hide');
    document.getElementById('login').classList.remove('hide');
}

function closeMoreMenu() {
    const m = document.getElementById('moreMenu');
    if(m) m.classList.remove('active');
}

function toggleMoreMenu() {
    const m = document.getElementById('moreMenu');
    if(m) m.classList.toggle('active');
}

function go(id) {
    const target = document.getElementById(id);
    if(!target) return;
    document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
    target.classList.add('active');
    document.querySelectorAll('.nav [data-section]').forEach(b => b.classList.toggle('active', b.dataset.section === id));

    const moreBtn = document.getElementById('moreBtn');
    if(moreBtn) moreBtn.classList.toggle('active', ['trafico','perfil','alertas'].includes(id));

    let t = {
        inicio: ['Mi jornada','Seguimiento activo del teléfono corporativo'],
        ruta: ['Crear ruta','Ruta del día creada por el vendedor'],
        mapa: ['Mapa','Ubicación real en producción'],
        checkin: ['Check-in','Registro de visitas con ubicación'],
        trafico: ['Tráfico','Validación de atrasos con ETA y tolerancia'],
        perfil: ['Mi perfil','Datos del vendedor y dispositivo'],
        alertas: ['Alertas','Eventos de manipulación y seguimiento']
    };

    document.getElementById('pageTitle').textContent = t[id][0];
    document.getElementById('pageSub').textContent = t[id][1];
    closeMoreMenu();
    window.scrollTo({top:0, left:0, behavior:'smooth'});

    const main = document.querySelector('.main');
    if(main) main.scrollTo({top:0, left:0, behavior:'smooth'});

    setTimeout(() => {
        if(id === 'mapa') initMap('routeMap');
        if(id === 'inicio') initMap('homeMap');
        if(id === 'checkin') cargarVisitasHoy();
        Object.values(maps).forEach(m => m.invalidateSize());
    }, 260);
}

document.querySelectorAll('.nav [data-section]').forEach(b => b.addEventListener('click', () => go(b.dataset.section)));

function dot(color) {
    return L.divIcon({
        className:'',
        html:`<div style="width:22px;height:22px;background:${color};border:4px solid white;border-radius:50%;box-shadow:0 4px 12px rgba(0,0,0,.25)"></div>`,
        iconSize:[22,22],iconAnchor:[11,11]
    });
}

function initMap(id) {
    if(maps[id]) { maps[id].invalidateSize(); return; }
    const el = document.getElementById(id);
    if(!el) return;
    if(typeof L === 'undefined') {
        el.innerHTML = '<div style="padding:22px;color:#6b7485">Mapa real de Guatemala. Se requiere internet para cargarlo.</div>';
        return;
    }
    let m = L.map(id, {zoomControl:true, scrollWheelZoom:true}).setView([14.615,-90.575], 12);
    maps[id] = m;
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {attribution:'© OpenStreetMap', maxZoom:19}).addTo(m);

    L.polyline(route.map(r => [r[0],r[1]]), {color:'#2573d8', weight:5}).addTo(m);
    L.polyline(route.map(r => [r[0],r[1]]), {color:'#2573d8', weight:30, opacity:.15}).addTo(m);

    markers[id] = L.marker([route[0][0],route[0][1]], {icon:dot('#1f9d63')}).addTo(m).bindPopup('<b>Tú estás aquí</b>');

    setTimeout(() => m.invalidateSize(), 500);
}

function actualizarUbicacionReal(lat, lng, direccionReal) {
    console.log("📍 UI Actualizada:", lat, lng, direccionReal);

    if (document.getElementById('lat')) {
        document.getElementById('lat').textContent = lat.toFixed(5);
        document.getElementById('lng').textContent = lng.toFixed(5);
        document.getElementById('lastLocation').textContent = direccionReal || "Ubicación detectada";
        document.getElementById('syncText').textContent = 'Recién actualizado';
    }

    if (markers['routeMap']) {
        markers['routeMap'].setLatLng([lat, lng]).bindPopup("<b>Ubicación Actual</b><br>" + direccionReal);
    }
    if (markers['homeMap']) {
        markers['homeMap'].setLatLng([lat, lng]);
    }
    if (maps['routeMap']) {
        maps['routeMap'].setView([lat, lng], 16);
    }
}

function move() {
    step = (step+1) % route.length;
    let r = route[step];
    actualizarUbicacionReal(r[0], r[1], r[2]);
}

function renderStops() {
    const list = document.getElementById('stopList');
    list.innerHTML = '';
    stops.forEach((s,i) => list.insertAdjacentHTML('beforeend', `<div class="routeStop"><div class="num">${i+1}</div><div><b>${s}</b><br><small style="color:var(--muted)">${['09:00','10:30','12:15','15:00'][i]||'Por definir'} · ${['Venta','Seguimiento','Mayoreo','Entrega'][i]||'Visita'}</small></div><span class="status ${i<2?'ok':'warn'}">${i<2?'Validado':'Pendiente'}</span></div>`));
    document.getElementById('routeStatus').textContent = 'Creada';
}

function addStop() {
    let nameInput = document.getElementById('destName');
    let name = nameInput.value.trim();

    if (name === '') {
        showToast('Aviso', 'Escribe el nombre del cliente primero');
        return;
    }

    stops.push(name);
    renderStops();

    nameInput.value = '';
    document.getElementById('destAddress').value = '';

    document.getElementById('sentBadge').className = 'status warn';
    document.getElementById('sentBadge').textContent = 'Cambios sin enviar';
}

function showToast(title,msg) {
    const t = document.getElementById('appToast');
    if(!t) return;
    t.innerHTML = '<b>'+title+'</b><small>'+msg+'</small>';
    t.classList.add('active');
    clearTimeout(window.toastTimer);
    window.toastTimer = setTimeout(() => t.classList.remove('active'), 5200);
}

async function submitRoute() {
    if (stops.length === 0) {
        showToast('Aviso', 'Agrega al menos una parada a la lista antes de enviar.');
        return;
    }

    const btn = document.getElementById('sentBadge');
    btn.textContent = 'Enviando...';

    const vendedorId = localStorage.getItem('vendedor_id') || 1;

    try {
        const response = await fetch(`${SERVIDOR_URL}/api/rutas/guardar`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                vendedor_id: vendedorId,
                paradas: stops
            })
        });

        const data = await response.json();

        if (data.exito) {
            btn.className = 'status ok';
            btn.textContent = 'Enviada al supervisor';
            if (document.getElementById('trafficEta')) document.getElementById('trafficEta').textContent = '2h 18m';
            showToast('Ruta enviada', 'La ruta fue registrada en la base de datos.');

            stops = [];
            renderStops();
        } else {
            btn.className = 'status warn';
            btn.textContent = 'Error al enviar';
            showToast('Error', 'No se pudo guardar la ruta.');
        }
    } catch (error) {
        console.error("Error al guardar ruta:", error);
        btn.className = 'status warn';
        btn.textContent = 'Error de conexión';
        alert("ERROR:" + error.message);
    }
}

async function cargarVisitasHoy() {
    const vendedorId = localStorage.getItem('vendedor_id') || 1;
    const tabla = document.getElementById('checkRows');

    if (tabla) tabla.innerHTML = '<tr><td colspan="3" style="text-align:center; padding: 15px; color:#667085;">Cargando...</td></tr>';

    try {
        const response = await fetch(`${SERVIDOR_URL}/api/visitas/hoy/${vendedorId}`);
        const data = await response.json();

        if (data.exito) {
            tabla.innerHTML = '';

            const fechaHoy = new Date().toLocaleDateString();

            const visitasDelDia = data.datos.filter(visita => {
                if (visita.fecha_visita) {
                    return new Date(visita.fecha_visita).toLocaleDateString() === fechaHoy;
                }
                return true; 
            });

            if (visitasDelDia.length === 0) {
                tabla.innerHTML = '<tr><td colspan="3" style="text-align:center; padding: 15px; color:#667085;">No hay check-ins hoy</td></tr>';
                return;
            }

            visitasDelDia.forEach(visita => {
                let horaFormateada = visita.hora;

                if (!horaFormateada && visita.fecha_visita) {
                    const dateObj = new Date(visita.fecha_visita);
                    horaFormateada = dateObj.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
                }

                tabla.insertAdjacentHTML('beforeend',
                    `<tr>
                        <td style="padding: 12px 0; border-bottom: 1px solid #f2f4f7;">${horaFormateada || '--:--'}</td>
                        <td style="padding: 12px 0; border-bottom: 1px solid #f2f4f7;"><b>${visita.nombre_cliente}</b></td>
                        <td style="padding: 12px 0; border-bottom: 1px solid #f2f4f7; color:#667085;">${visita.resultado}</td>
                    </tr>`
                );
            });
        } else {
            if (tabla) tabla.innerHTML = `<tr><td colspan="3" style="text-align:center; padding: 15px; color:#d92d20;">${data.mensaje || 'Error al obtener datos'}</td></tr>`;
        }
    } catch (error) {
        console.error("Error al cargar la tabla:", error);
        if (tabla) tabla.innerHTML = '<tr><td colspan="3" style="text-align:center; padding: 15px; color:#d92d20;">Error de conexión con el servidor</td></tr>';
    }
}

async function hacerCheckIn() {
    const vendedorId = localStorage.getItem('vendedor_id') || 1;

    const latEl = document.getElementById('lat');
    const lngEl = document.getElementById('lng');
    const locEl = document.getElementById('lastLocation');

    const latText = latEl ? latEl.textContent : '0.00000';
    const lngText = lngEl ? lngEl.textContent : '0.00000';
    const direccion = locEl ? locEl.textContent : 'Ubicación en proceso';

    const nombreCliente = document.getElementById('chkCliente').value;
    const resultado = document.getElementById('chkResultado').value;
    const comentario = document.getElementById('chkComentario').value;

    try {
        const response = await fetch(`${SERVIDOR_URL}/api/visitas/checkin`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                usuario_id: vendedorId,
                nombre_cliente: nombreCliente,
                resultado: resultado,
                comentario: comentario,
                latitud: parseFloat(latText),
                longitud: parseFloat(lngText),
                direccion: direccion
            })
        });

        const data = await response.json();
        if (data.exito) {
            if (window.AndroidBridge) AndroidBridge.mostrarMensajeNativo("Check-in guardado: " + nombreCliente);
            cargarVisitasHoy();
        } else {
            if (window.AndroidBridge) AndroidBridge.mostrarMensajeNativo("Error al guardar en BD");
        }
    } catch (error) {
        console.error("Error en Check-In:", error);
        if (window.AndroidBridge) AndroidBridge.mostrarMensajeNativo("Error de red (Bloqueo de IP o Firewall)");
    }
}

window.addEventListener('resize', () => setTimeout(() => Object.values(maps).forEach(m => m.invalidateSize()), 200));