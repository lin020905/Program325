"use strict";

const app = document.querySelector("#app");
const dialog = document.querySelector("#dialog");
const notice = document.querySelector("#notice");
const statuses = {
    PENDING: "待审批", APPROVED: "已通过", REJECTED: "已驳回",
    CANCELLED: "已取消", EXPIRED: "已过期"
};
const pages = {
    rooms: ["浏览会议室", "▦"],
    mine: ["我的预约", "≡"],
    manage: ["会议室管理", "▤", true],
    pending: ["预约审批", "✓", true],
    records: ["全部预约", "≡", true],
    schedule: ["排期日历", "▦", true]
};
let user;
let route;
let noticeTimer;

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, character => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[character]);
}

const text = escapeHtml;
const beijingNow = () => new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString().slice(0, 19);
const today = () => beijingNow().slice(0, 10);
const addDays = (date, days) =>
    new Date(Date.parse(date + "T00:00:00Z") + days * 86400000).toISOString().slice(0, 10);
const dateTime = value => value ? value.slice(0, 16).replace("T", " ") : "—";
const clock = hour => String(hour).padStart(2, "0") + ":00";
const input = (form, name) => form.elements.namedItem(name);
const option = (value, label) => `<option value="${text(value)}">${text(label)}</option>`;
const roomOptions = rooms => rooms.map(room => option(room.roomId, `${room.name} · ${room.roomNo}`)).join("");
const statusLabel = item => item.confirmedAt && item.status === "APPROVED" ? "已确认使用" : statuses[item.status];
const badge = (label, type) => `<span class="badge ${text(type.toLowerCase())}">${text(label)}</span>`;
const roomBadge = room => room.bookable ? badge("可预约", "approved") : badge("暂停预约", "cancelled");
const button = (label, action, id, style = "secondary") =>
    `<button type="button" class="button ${style}" data-action="${action}" data-id="${id}">${label}</button>`;
const empty = message => `<div class="empty">${text(message)}</div>`;
const panelError = '<p class="form-error" role="alert" hidden></p>';

function showNotice(message) {
    clearTimeout(noticeTimer);
    notice.textContent = message;
    notice.hidden = false;
    noticeTimer = setTimeout(() => { notice.hidden = true; }, 4200);
}

function showError(form, error) {
    if (!form.isConnected || handleSessionExpiry(error)) return;
    const target = form.querySelector(".form-error");
    target.textContent = error.message;
    target.hidden = false;
}

const loginRoute = role => role === "ADMIN" ? "login-admin" : "login-user";

function handleSessionExpiry(error) {
    if (error.code !== "UNAUTHENTICATED" || !user) return false;
    history.replaceState(null, "", "#" + loginRoute(user.role));
    user = null;
    void render();
    showNotice("登录已失效，请重新登录");
    return true;
}

function bindForm(form, submit) {
    form.addEventListener("submit", async event => {
        event.preventDefault();
        form.querySelector(".form-error").hidden = true;
        const submitButton = form.querySelector('button[type="submit"]');
        submitButton.disabled = true;
        try {
            await submit(form, new FormData(form));
        } catch (error) {
            showError(form, error);
        } finally {
            submitButton.disabled = false;
        }
    });
}

function openDialog(title, body) {
    dialog.innerHTML = `
        <div class="dialog-head">
            <h2 id="dialog-title">${text(title)}</h2>
            <button class="icon-button" type="button" data-action="close" aria-label="关闭弹窗">×</button>
        </div>
        <div class="dialog-body">${body}</div>`;
    if (dialog.open) {
        dialog.querySelector(".icon-button").focus();
    } else {
        dialog.showModal();
    }
}

function confirmAction(title, description, label, submit) {
    openDialog(title, `<p class="muted">${text(description)}</p>
        <form>${panelError}<div class="dialog-actions">
            <button type="button" class="button secondary" data-action="close">返回</button>
            <button type="submit" class="button danger">${label}</button>
        </div></form>`);
    bindForm(dialog.querySelector("form"), submit);
}

function go(page) {
    if (location.hash.slice(1) === page) {
        void render();
    } else {
        location.hash = page;
    }
}

function loginPage(admin) {
    const title = admin ? "管理员登录" : "预约用户登录";
    app.innerHTML = `
        <main class="login-layout">
            <div class="login-cover">
                <div class="brand"><span class="brand-icon" aria-hidden="true"></span> 会议室预约系统</div>
                <div class="login-intro">
                    <p class="eyebrow">会议中心</p>
                    <h1>为每场会议<br>安排合适的空间</h1>
                    <p>按日期、时段和人数找到空闲会议室，在线提交申请并跟踪审批进度。</p>
                </div>
                <div class="login-pattern" aria-hidden="true"><i></i><i></i><i></i></div>
            </div>
            <div class="login-content">
                <section class="login-card" aria-labelledby="login-title">
                    <nav class="login-roles" aria-label="登录角色">
                        <a href="#login-user" ${!admin ? 'aria-current="page"' : ""}>预约用户入口</a>
                        <a href="#login-admin" ${admin ? 'aria-current="page"' : ""}>管理员入口</a>
                    </nav>
                    <h2 id="login-title">${title}</h2>
                    <form id="login-form">
                        <label>账号<input name="username" required autocomplete="username" placeholder="输入账号"></label>
                        <label>密码<input name="password" type="password" required autocomplete="current-password" placeholder="输入密码"></label>
                        ${panelError}
                        <button type="submit" class="button primary">登录</button>
                    </form>
                </section>
            </div>
        </main>`;
    bindForm(document.querySelector("#login-form"), async (_, fields) => {
        await login(fields.get("username"), fields.get("password"));
    });
}

async function login(username, password) {
    user = await meetingApi.login({ username, password, role: route === "login-admin" ? "ADMIN" : "USER" });
    go("rooms");
}

function workspace() {
    const nav = Object.entries(pages).filter(([, page]) => !page[2] || user.role === "ADMIN").map(
        ([key, [label, icon]]) => `<a href="#${key}" ${key === route ? 'aria-current="page"' : ""}>
            <span class="nav-icon" aria-hidden="true">${icon}</span>${label}</a>`).join("");
    app.innerHTML = `
        <div class="workspace">
            <aside class="sidebar">
                <a class="brand" href="#rooms"><span class="brand-icon" aria-hidden="true"></span>
                    <span>会议室预约<span class="brand-subtitle">会议中心</span></span>
                </a>
                <nav class="side-nav" aria-label="功能导航">${nav}</nav>
            </aside>
            <header class="topbar">
                <span class="breadcrumb">会议中心 <span aria-hidden="true">/</span> ${pages[route][0]}</span>
                <div class="account">
                    <span class="avatar" aria-hidden="true">${user.role === "ADMIN" ? "管" : "约"}</span>
                    <span class="account-name">${text(user.username)}<small>${user.role === "ADMIN" ? "管理员" : "预约用户"}</small></span>
                    <button class="button secondary small" type="button" data-action="logout">退出登录</button>
                </div>
            </header>
            <main class="content" id="page-content" aria-label="${pages[route][0]}"></main>
        </div>`;
    return document.querySelector("#page-content");
}

function heading(title, description, action = "") {
    return `<header class="page-heading">
        <div><h1>${title}</h1><p class="muted">${description}</p></div>${action}
    </header>`;
}

function hours(first, last) {
    return Array.from({ length: last - first + 1 }, (_, index) => {
        const hour = first + index;
        return option(String(hour).padStart(2, "0"), clock(hour) + (hour === 24 ? "（次日 00:00）" : ""));
    }).join("");
}

function getWindow(form, required = false) {
    const date = input(form, "date").value;
    const start = input(form, "start").value;
    const end = input(form, "end").value;
    const count = input(form, "attendees").value;
    const attendees = count ? Number(count) : undefined;
    if (!date && !start && !end && !required) return { attendees };
    if (!date || !start || !end) throw new Error("请填写预约日期、开始时间和结束时间");
    if (end <= start) throw new Error("结束时间须晚于开始时间");
    const startTime = date + "T" + start + ":00:00";
    const endTime = end === "24" ? addDays(date, 1) + "T00:00:00" : date + "T" + end + ":00:00";
    if (startTime <= beijingNow()) throw new Error("请选择尚未开始的时段");
    return { attendees, startTime, endTime };
}

function bookingFields(required = false) {
    const needed = required ? "required" : "";
    return `
        <label>预约日期<input type="date" name="date" min="${today()}" ${needed}></label>
        <label>开始时间<select name="start" ${needed}><option value="">请选择</option>${hours(0, 23)}</select></label>
        <label>结束时间<select name="end" ${needed}><option value="">请选择</option>${hours(1, 24)}</select></label>
        <label>参会人数<input type="number" name="attendees" min="1" max="2147483647" placeholder="输入人数" ${needed}></label>`;
}

async function showRooms(surface) {
    surface.innerHTML = `
        <section class="room-hero">
            <div><p class="eyebrow">会议中心 / 空间预约</p>
                <h1>浏览会议室</h1><p>按日期、整点时段和参会人数查询合适的会议室。</p>
            </div>
            <div class="hero-calendar" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i><i></i></div>
        </section>
        <form class="panel room-search" id="room-search" aria-label="查询会议室">
            <div class="booking-fields">${bookingFields()}</div>
            <div class="filter-actions">
                <button type="reset" class="button secondary">重置</button>
                <button type="submit" class="button primary">查询会议室</button>
            </div>
            ${panelError}
        </form>
        <div class="section-heading"><h2>会议室列表</h2><span class="muted" id="room-count" role="status"></span></div>
        <div id="room-list" class="room-grid"></div>`;
    const form = surface.querySelector("form");
    let latestSearch = 0;
    const load = async filters => {
        const search = ++latestSearch;
        const rooms = await meetingApi.listRooms(filters);
        if (search !== latestSearch) return;
        surface.querySelector("#room-count").textContent = `共 ${rooms.length} 间`;
        surface.querySelector("#room-list").innerHTML = rooms.map(roomCard).join("")
            || empty("没有符合条件的会议室");
    };
    bindForm(form, () => load(getWindow(form)));
    form.addEventListener("reset", () => {
        form.querySelector(".form-error").hidden = true;
        load().catch(error => showError(form, error));
    });
    await load();
}

function roomCard(room) {
    return `<article class="panel room-card">
        <div class="room-card-head"><span class="room-icon" aria-hidden="true">▦</span>${roomBadge(room)}</div>
        <h3>${text(room.name)}</h3><p class="room-number">${text(room.roomNo)}</p>
        <p class="room-purpose">${text(room.purpose || "暂无用途说明")}</p>
        <dl class="room-specs"><div><dt>容纳人数</dt><dd>${room.capacity} 人</dd></div>
            <div><dt>面积</dt><dd>${room.area} m²</dd></div></dl>
        <div class="card-actions">
            ${button("详情", "room-detail", room.roomId)}
            ${room.bookable ? button("预约", "book", room.roomId, "primary") : '<button type="button" class="button secondary" disabled>暂不可约</button>'}
        </div>
    </article>`;
}

function details(fields) {
    return `<dl class="details">${fields.map(([label, value]) =>
        `<div><dt>${label}</dt><dd>${text(value ?? "—")}</dd></div>`).join("")}</dl>`;
}

async function roomDetail(id) {
    const room = await meetingApi.roomDetail(id);
    openDialog(room.name, details([
        ["房号", room.roomNo], ["容纳人数", room.capacity + " 人"], ["面积", room.area + " m²"],
        ["预约状态", room.bookable ? "可预约" : "暂停预约"], ["用途", room.purpose || "—"]
    ]) + (room.bookable ? `<div class="dialog-actions">${button("预约", "book", id, "primary")}</div>` : ""));
}

async function book(id) {
    const search = document.querySelector("#room-search");
    const room = await meetingApi.roomDetail(id);
    if (!search.isConnected) return;
    openDialog("预约 " + room.name, `
        <p class="muted">${text(room.roomNo)} · 容量 ${room.capacity} 人 · 北京时间</p>
        <form>
            <div class="dialog-fields">${bookingFields(true)}</div>
            <label>会议主题<input name="subject" maxlength="200" required placeholder="输入会议主题"></label>
            ${panelError}
            <div class="dialog-actions"><button class="button primary" type="submit">提交申请</button></div>
        </form>`);
    const form = dialog.querySelector("form");
    for (const name of ["date", "start", "end", "attendees"]) {
        input(form, name).value = input(search, name).value;
    }
    input(form, "attendees").max = room.capacity;
    bindForm(form, async (_, fields) => {
        await meetingApi.createReservation({ ...getWindow(form, true), roomId: id, subject: fields.get("subject") });
        dialog.close();
        showNotice("预约已提交，等待审批");
        go("mine");
    });
}

function table(headers, rows) {
    return `<div class="table-wrap" tabindex="0" role="region" aria-label="数据表格">
        <table><thead><tr>${headers.map(label => `<th scope="col">${label}</th>`).join("")}</tr></thead>
        <tbody>${rows}</tbody></table></div>`;
}

async function showReservations(surface, scope) {
    const title = { mine: "我的预约", pending: "预约审批", records: "全部预约" }[scope];
    const description = {
        mine: "查看预约状态、审批结果，或管理仍可操作的预约。",
        pending: "处理待审批的预约申请。驳回时须填写理由。",
        records: "按会议室和状态查询预约记录及处理详情。"
    }[scope];
    surface.innerHTML = heading(title, description) + `
        <section class="panel list-panel">
            <form class="toolbar" aria-label="预约记录筛选">
                ${scope === "records" ? '<label>会议室<select name="room"><option value="">全部会议室</option></select></label>' : ""}
                ${scope !== "pending" ? `<label>预约状态<select name="status"><option value="">全部状态</option>
                    ${Object.entries(statuses).map(([value, label]) => option(value, label)).join("")}
                </select></label><button class="button primary" type="submit">查询</button>` : ""}
                ${panelError}
            </form>
            <div class="records-result"></div>
            <div class="pagination"><span class="muted" role="status"></span><div>
                <button type="button" class="button secondary small" data-page="-1" disabled>上一页</button>
                <button type="button" class="button secondary small" data-page="1" disabled>下一页</button>
            </div></div>
        </section>`;
    const form = surface.querySelector("form");
    if (scope === "records") {
        input(form, "room").innerHTML = option("", "全部会议室") + roomOptions(await meetingApi.listRooms());
    }
    const result = surface.querySelector(".records-result");
    const previous = surface.querySelector('[data-page="-1"]');
    const next = surface.querySelector('[data-page="1"]');
    let currentPage = 1;
    let filters = {};
    let latestSearch = 0;
    const load = async (page, nextFilters = filters) => {
        const search = ++latestSearch;
        const data = await meetingApi.listReservations(scope, { ...nextFilters, page });
        if (search !== latestSearch) return;
        filters = nextFilters;
        currentPage = data.page;
        previous.disabled = currentPage <= 1;
        next.disabled = currentPage * data.size >= data.total;
        surface.querySelector(".pagination span").textContent = `共 ${data.total} 条 · 第 ${currentPage} 页`;
        const rows = data.items.map(item => `<tr>
            <td><strong>${text(item.subject)}</strong>${scope !== "mine" ? `<small>${text(item.username)}</small>` : ""}</td>
            <td>${text(item.roomName)}<small>${text(item.roomNo)}</small></td>
            <td class="time-cell">${dateTime(item.startTime)}<small>至 ${dateTime(item.endTime)}</small></td>
            <td>${item.attendees} 人</td><td>${badge(statusLabel(item), item.status)}</td>
            <td><div class="row-actions">${button("详情", "reservation-detail", item.reservationId)}
                ${scope === "pending" ? button("审批", "approval", item.reservationId, "primary") : ""}</div></td>
        </tr>`).join("");
        result.innerHTML = rows ? table(["会议主题", "会议室", "预约时段", "人数", "状态", "操作"], rows)
            : empty(scope === "pending" ? "暂无待审批的预约" : "没有符合条件的预约记录");
    };
    if (scope !== "pending") {
        bindForm(form, (_, fields) => {
            return load(1, { status: fields.get("status"), roomId: fields.get("room") });
        });
    }
    for (const control of [previous, next]) {
        control.addEventListener("click", () => {
            form.querySelector(".form-error").hidden = true;
            load(currentPage + Number(control.dataset.page)).catch(error => showError(form, error));
        });
    }
    await load(1);
}

async function reservationDetail(id) {
    const own = route === "mine";
    const item = await meetingApi.reservationDetail(id, own);
    const now = Date.now();
    const start = Date.parse(item.startTime + "+08:00");
    const end = Date.parse(item.endTime + "+08:00");
    const activeApproval = item.status === "APPROVED" && !item.confirmedAt && now < end;
    const cancellable = (item.status === "PENDING" && now < start) || activeApproval;
    const confirmable = activeApproval && now >= start - 30 * 60 * 1000;
    openDialog(item.subject, details([
        ["申请人", item.username], ["会议室", `${item.roomName} · ${item.roomNo}`], ["参会人数", item.attendees + " 人"],
        ["状态", statusLabel(item)], ["开始时间", dateTime(item.startTime)], ["结束时间", dateTime(item.endTime)],
        ["提交时间", dateTime(item.createdAt)], ["审批结果", statuses[item.approvalResult]], ["审批人", item.approverName],
        ["审批时间", dateTime(item.approvedAt)], ["审批理由", item.approvalReason || "—"],
        ["取消时间", dateTime(item.cancelledAt)], ["使用确认时间", dateTime(item.confirmedAt)]
    ]) + `<div class="dialog-actions">
        ${own && cancellable ? button("取消预约", "cancel", id, "danger-outline") : ""}
        ${own && confirmable ? button("确认使用", "confirm", id, "primary") : ""}
        ${route === "pending" ? button("审批", "approval", id, "primary") : ""}
    </div>`);
}

async function approval(id) {
    const item = await meetingApi.reservationDetail(id, false);
    openDialog("审批预约", `<p class="muted">${text(item.subject)} · ${text(item.roomName)} · ${item.attendees} 人</p>
        <form>
            <label>审批结果<select name="result">${option("APPROVED", "通过")}${option("REJECTED", "驳回")}</select></label>
            <label>审批理由<textarea name="reason" maxlength="500" rows="3" placeholder="驳回时必填"></textarea></label>
            ${panelError}
            <div class="dialog-actions"><button class="button primary" type="submit">提交审批</button></div>
        </form>`);
    const form = dialog.querySelector("form");
    input(form, "result").addEventListener("change", () => {
        input(form, "reason").required = input(form, "result").value === "REJECTED";
    });
    bindForm(form, async (_, fields) => {
        await meetingApi.approveReservation(id, Object.fromEntries(fields));
        dialog.close();
        showNotice("审批结果已记录");
        await render();
    });
}

async function showManagement(surface) {
    const rooms = await meetingApi.listRooms();
    surface.innerHTML = heading("会议室管理", "维护会议室资料和预约状态。", button("新增会议室", "edit-room", 0, "primary"))
        + `<section class="panel list-panel">${rooms.length ? table(
            ["会议室", "房号", "容量", "面积", "用途", "状态", "操作"],
            rooms.map(room => `<tr><td><strong>${text(room.name)}</strong></td><td>${text(room.roomNo)}</td>
                <td>${room.capacity} 人</td><td>${room.area} m²</td><td>${text(room.purpose || "—")}</td>
                <td>${roomBadge(room)}</td><td><div class="row-actions">
                    ${button("编辑", "edit-room", room.roomId)}${button("删除", "delete-room", room.roomId, "danger-outline")}
                </div></td></tr>`).join("")) : empty("暂无会议室")}</section>`;
}

async function editRoom(id) {
    const room = id ? await meetingApi.roomDetail(id) : { bookable: true };
    openDialog(id ? "编辑会议室" : "新增会议室", `<form>
        <div class="dialog-fields">
            <label>会议室名称<input name="name" required maxlength="100" value="${text(room.name)}"></label>
            <label>房号<input name="roomNo" required maxlength="30" value="${text(room.roomNo)}"></label>
            <label>容量<input name="capacity" type="number" required min="1" max="2147483647" value="${text(room.capacity)}"></label>
            <label>面积（m²）<input name="area" type="number" required min="0.01" max="999999.99" step="0.01" value="${text(room.area)}"></label>
        </div>
        <label>用途<textarea name="purpose" rows="3" required maxlength="255">${text(room.purpose)}</textarea></label>
        <label class="check-label"><input type="checkbox" name="bookable" ${room.bookable ? "checked" : ""}>可预约</label>
        ${panelError}
        <div class="dialog-actions"><button class="button primary" type="submit">保存会议室</button></div>
    </form>`);
    bindForm(dialog.querySelector("form"), async (_, fields) => {
        await meetingApi.saveRoom(id, {
            ...Object.fromEntries(fields), capacity: Number(fields.get("capacity")),
            area: Number(fields.get("area")), bookable: fields.has("bookable")
        });
        dialog.close();
        showNotice("会议室已保存");
        await render();
    });
}

async function showSchedule(surface) {
    surface.innerHTML = heading("排期日历", "按日查看全部会议室，或按周查看指定会议室的占用时段。")
        + `<section class="panel list-panel">
            <form class="toolbar" aria-label="排期筛选">
                <label>排期模式<select name="mode">${option("day", "按日查看")}${option("week", "按周查看")}</select></label>
                <label>排期日期<input type="date" name="date" value="${today()}" required></label>
                <label hidden>会议室<select name="room"></select></label>
                ${panelError}
            </form>
            <div class="schedule-legend">${badge("待审批", "pending")} ${badge("已通过", "approved")}
                <span class="muted">北京时间</span></div>
            <div class="schedule-result"></div>
        </section>`;
    const form = surface.querySelector("form");
    const result = surface.querySelector(".schedule-result");
    const roomField = input(form, "room");
    const rooms = await meetingApi.listRooms();
    roomField.innerHTML = roomOptions(rooms);
    let latestSearch = 0;
    const load = async () => {
        const search = ++latestSearch;
        form.querySelector(".form-error").hidden = true;
        const week = input(form, "mode").value === "week";
        roomField.closest("label").hidden = !week;
        const from = input(form, "date").value;
        if (!from) return;
        const data = await meetingApi.schedule({ from, days: week ? 7 : 1, roomId: week ? roomField.value : undefined });
        if (search !== latestSearch) return;
        if (!data.rooms.length) {
            result.innerHTML = empty("暂无会议室排期");
            return;
        }
        const columns = week
            ? Array.from({ length: 7 }, (_, i) => ({ date: addDays(from, i), room: data.rooms[0] }))
            : data.rooms.map(room => ({ date: from, room }));
        const rows = Array.from({ length: 24 }, (_, hour) => `<tr>
            <th scope="row">${clock(hour)}</th>
            ${columns.map(({ date, room }) => {
                const start = date + "T" + clock(hour) + ":00";
                const end = hour === 23 ? addDays(date, 1) + "T00:00:00" : date + "T" + clock(hour + 1) + ":00";
                const slot = data.reservations.find(item => item.roomId === room.roomId && item.startTime < end && item.endTime > start);
                return `<td>${slot ? badge(slot.subject, slot.status) : '<span class="slot-empty">—</span>'}</td>`;
            }).join("")}</tr>`).join("");
        result.innerHTML = table(["时间", ...columns.map(({ date, room }) => text(week ? date.slice(5) : room.name))], rows);
    };
    form.addEventListener("submit", event => event.preventDefault());
    form.addEventListener("change", () => load().catch(error => showError(form, error)));
    await load();
}

const actions = {
    async logout() {
        const destination = loginRoute(user.role);
        await meetingApi.logout();
        user = null;
        clearTimeout(noticeTimer);
        notice.hidden = true;
        go(destination);
    },
    close: () => dialog.close(),
    "room-detail": roomDetail,
    book,
    "reservation-detail": reservationDetail,
    approval,
    "edit-room": editRoom,
    "delete-room": id => confirmAction("删除会议室", "确认删除该会议室？已有历史预约仍可查询。", "确认删除", async () => {
        await meetingApi.deleteRoom(id);
        dialog.close();
        showNotice("会议室已删除");
        await render();
    }),
    cancel: id => confirmAction("取消预约", "确认取消后，该时段将释放给其他用户。", "确认取消", async () => {
        await meetingApi.cancelReservation(id);
        dialog.close();
        showNotice("预约已取消");
        await render();
    }),
    async confirm(id) {
        await meetingApi.confirmReservation(id);
        await render();
        await reservationDetail(id);
        showNotice("已确认使用");
    }
};

document.addEventListener("click", async event => {
    const control = event.target.closest("button[data-action]");
    if (!control) return;
    control.disabled = true;
    try {
        await actions[control.dataset.action](Number(control.dataset.id));
    } catch (error) {
        if (!control.isConnected || handleSessionExpiry(error)) return;
        if (dialog.open) {
            let message = dialog.querySelector(".action-error");
            if (!message) {
                message = document.createElement("p");
                message.className = "form-error action-error";
                message.setAttribute("role", "alert");
                dialog.querySelector(".dialog-body").append(message);
            }
            message.textContent = error.message;
        } else {
            showNotice(error.message);
        }
    } finally {
        control.disabled = false;
    }
});

async function render() {
    if (dialog.open) dialog.close();
    const requested = location.hash.slice(1);
    if (!user) {
        const admin = requested === "login-admin" || (Object.hasOwn(pages, requested) && pages[requested][2]);
        route = loginRoute(admin ? "ADMIN" : "USER");
        loginPage(route === "login-admin");
        return;
    }
    route = Object.hasOwn(pages, requested) && (!pages[requested][2] || user.role === "ADMIN") ? requested : "rooms";
    const surface = workspace();
    try {
        switch (route) {
            case "rooms": await showRooms(surface); break;
            case "mine": case "pending": case "records": await showReservations(surface, route); break;
            case "manage": await showManagement(surface); break;
            case "schedule": await showSchedule(surface); break;
        }
    } catch (error) {
        if (surface.isConnected && !handleSessionExpiry(error)) surface.innerHTML = empty(error.message);
    }
}

async function start() {
    try {
        user = await meetingApi.session();
    } catch (error) {
        if (error.code !== "UNAUTHENTICATED") showNotice(error.message);
    }
    window.addEventListener("hashchange", render);
    await render();
}

void start();
