"use strict";

const meetingApi = (() => {
    const reservationPaths = {
        mine: "/reservations",
        pending: "/admin/reservations/pending",
        records: "/admin/reservations"
    };

    async function request(path, { method = "GET", query = {}, body } = {}) {
        const params = new URLSearchParams();
        for (const [name, value] of Object.entries(query)) {
            if (value != null && value !== "") params.set(name, value);
        }
        const search = params.toString();
        const headers = { Accept: "application/json" };
        if (body !== undefined) headers["Content-Type"] = "application/json";

        let response;
        try {
            response = await fetch("/api" + path + (search ? "?" + search : ""), {
                method,
                headers,
                credentials: "same-origin",
                cache: "no-store",
                body: body === undefined ? undefined : JSON.stringify(body)
            });
        } catch {
            throw new Error("无法连接服务器，请检查网络后重试");
        }

        const payload = await response.json().catch(() => null);
        if (!response.ok || payload?.code !== "OK") {
            const error = new Error(payload?.message || `服务器返回异常（HTTP ${response.status}）`);
            error.code = payload?.code;
            throw error;
        }
        return payload.data;
    }

    const reservationPath = (id, own) => `${reservationPaths[own ? "mine" : "records"]}/${id}`;

    return {
        login: body => request("/auth/login", { method: "POST", body }),
        session: () => request("/auth/session"),
        logout: () => request("/auth/session", { method: "DELETE" }),
        listRooms: query => request("/rooms", { query }),
        roomDetail: id => request(`/rooms/${id}`),
        saveRoom: (id, body) => id
            ? request(`/admin/rooms/${id}`, { method: "PUT", body })
            : request("/admin/rooms", { method: "POST", body }),
        deleteRoom: id => request(`/admin/rooms/${id}`, { method: "DELETE" }),
        createReservation: body => request(reservationPaths.mine, { method: "POST", body }),
        listReservations(scope, { status, roomId, page = 1, size = 10 } = {}) {
            const query = { page, size };
            if (scope !== "pending") query.status = status;
            if (scope === "records") query.roomId = roomId;
            return request(reservationPaths[scope], { query });
        },
        reservationDetail: (id, own) => request(reservationPath(id, own)),
        cancelReservation: id => request(reservationPath(id, true) + "/cancel", { method: "POST" }),
        confirmReservation: id => request(reservationPath(id, true) + "/confirm", { method: "POST" }),
        approveReservation: (id, body) => request(reservationPath(id, false) + "/approval", { method: "POST", body }),
        schedule: query => request("/admin/schedule", { query })
    };
})();
