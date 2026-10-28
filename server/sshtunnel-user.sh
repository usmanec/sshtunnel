#!/usr/bin/env bash
# SSH Tunnel: creates SSH users that can only forward TCP connections (ssh -D),
# without a shell, SFTP, remote forwarding, agent or X11 forwarding.
#
# Usage on the server (root or sudo):
#   curl -fsSLO https://raw.githubusercontent.com/usmanec/sshtunnel/main/server/sshtunnel-user.sh
#   sudo bash sshtunnel-user.sh            # interactive menu
#   sudo bash sshtunnel-user.sh add|remove|list
# Piping also works (questions are read from the terminal):
#   curl -fsSL https://raw.githubusercontent.com/usmanec/sshtunnel/main/server/sshtunnel-user.sh | sudo bash
#
# The first question is the language (the default follows LANG); SSHTUNNEL_LANG=ru|en skips it.
# Every user gets its own "Match User" block: a drop-in file in /etc/ssh/sshd_config.d
# (OpenSSH 8.2+ with Include) or a marked block at the end of /etc/ssh/sshd_config.
# The config is validated with "sshd -t" and rolled back if it is rejected.

set -euo pipefail
umask 022

GROUP="sshtunnel"
MAIN_CONF="/etc/ssh/sshd_config"
CONF_DIR="/etc/ssh/sshd_config.d"
DEFAULT_DNS="1.1.1.1"
WORK_DIR=""

# ---------- output ----------

case "${SSHTUNNEL_LANG:-${LC_ALL:-${LC_MESSAGES:-${LANG:-}}}}" in
    ru*) RU=1 ;;
    *) RU=0 ;;
esac
# t "English" "Russian"
t() { if [ "$RU" = 1 ]; then printf '%s' "$2"; else printf '%s' "$1"; fi; }

if [ -t 2 ]; then
    BOLD=$'\e[1m'; RED=$'\e[31m'; GREEN=$'\e[32m'; YELLOW=$'\e[33m'; RESET=$'\e[0m'
else
    BOLD=""; RED=""; GREEN=""; YELLOW=""; RESET=""
fi

# All messages go to stderr: stdout of helpers is captured by $(...)
say() { printf '%s\n' "$*" >&2; }
info() { printf '%s==>%s %s\n' "$GREEN" "$RESET" "$*" >&2; }
warn() { printf '%s!!%s %s\n' "$YELLOW" "$RESET" "$*" >&2; }
die() { printf '%s%s%s %s\n' "$RED" "$(t "Error:" "Ошибка:")" "$RESET" "$*" >&2; exit 1; }
# field "Label" "value": aligned "  Label: value" (${#} counts characters, not bytes, in UTF-8)
field() {
    local label="$1:"
    printf '  %s%*s %s\n' "$label" $((14 - ${#label})) '' "$2" >&2
}
title() { printf '\n%s%s%s\n' "$BOLD" "$*" "$RESET" >&2; }

cleanup() { if [ -n "$WORK_DIR" ]; then rm -rf "$WORK_DIR"; fi; }
trap cleanup EXIT

# ---------- input (works with "curl ... | sudo bash") ----------

open_tty() {
    if (exec </dev/tty) 2>/dev/null; then
        exec 3</dev/tty
    else
        die "$(t "No terminal for questions. Download the script and run it with: sudo bash sshtunnel-user.sh" \
                 "Нет терминала для вопросов. Скачайте скрипт и запустите: sudo bash sshtunnel-user.sh")"
    fi
}

# ask "Prompt" [default] -> answer on stdout
ask() {
    local prompt=$1 default=${2-} answer
    if [ -n "$default" ]; then
        printf '%s [%s]: ' "$prompt" "$default" >&2
    else
        printf '%s: ' "$prompt" >&2
    fi
    IFS= read -r -u 3 answer || die "$(t "input closed" "ввод прерван")"
    printf '%s' "${answer:-$default}"
}

ask_secret() {
    local prompt=$1 answer
    printf '%s: ' "$prompt" >&2
    IFS= read -r -s -u 3 answer || die "$(t "input closed" "ввод прерван")"
    printf '\n' >&2
    printf '%s' "$answer"
}

# confirm "Question" y|n -> exit status
confirm() {
    local prompt=$1 default=$2 hint answer
    if [ "$default" = y ]; then hint="$(t "Y/n" "Д/н")"; else hint="$(t "y/N" "д/Н")"; fi
    while true; do
        printf '%s [%s]: ' "$prompt" "$hint" >&2
        IFS= read -r -u 3 answer || die "$(t "input closed" "ввод прерван")"
        answer=$(printf '%s' "${answer:-$default}" | tr '[:upper:]' '[:lower:]')
        case "$answer" in
            y | yes | д | да) return 0 ;;
            n | no | н | нет) return 1 ;;
        esac
    done
}

# choose "Title" "option 1" "option 2" ... -> number on stdout (1 = default)
choose() {
    local header=$1 i answer option
    shift
    title "$header"
    i=1
    for option in "$@"; do
        printf '  %d) %s\n' "$i" "$option" >&2
        i=$((i + 1))
    done
    while true; do
        answer=$(ask "$(t "Choose" "Выберите")" 1)
        if [[ "$answer" =~ ^[0-9]+$ ]] && [ "$answer" -ge 1 ] && [ "$answer" -le $# ]; then
            printf '%s' "$answer"
            return
        fi
    done
}

# Asked first, in both languages; skipped when SSHTUNNEL_LANG is set
choose_language() {
    local default=1 answer
    if [ -n "${SSHTUNNEL_LANG:-}" ]; then return; fi
    if [ "$RU" = 1 ]; then default=2; fi
    printf '\n%sLanguage / Язык%s\n  1) English\n  2) Русский\n' "$BOLD" "$RESET" >&2
    while true; do
        printf 'Choose / Выберите [%s]: ' "$default" >&2
        IFS= read -r -u 3 answer || die "input closed / ввод прерван"
        case "${answer:-$default}" in
            1) RU=0; return ;;
            2) RU=1; return ;;
        esac
    done
}

# ---------- system ----------

SSHD=""
NOLOGIN=""

check_system() {
    [ "$(id -u)" = 0 ] || die "$(t "run as root: sudo bash sshtunnel-user.sh" "запустите от root: sudo bash sshtunnel-user.sh")"
    SSHD=$(command -v sshd || true)
    [ -n "$SSHD" ] || { [ -x /usr/sbin/sshd ] && SSHD=/usr/sbin/sshd; } || die "$(t "OpenSSH server (sshd) not found" "не найден сервер OpenSSH (sshd)")"
    [ -f "$MAIN_CONF" ] || die "$(t "not found:" "не найден:") $MAIN_CONF"
    for candidate in /usr/sbin/nologin /sbin/nologin /usr/bin/nologin /bin/false; do
        if [ -x "$candidate" ]; then NOLOGIN=$candidate; break; fi
    done
    [ -n "$NOLOGIN" ] || die "$(t "nologin not found" "не найден nologin")"
    local output
    if ! output=$("$SSHD" -t 2>&1); then
        die "$(t "the current sshd config is already invalid, fix it first:" "текущий конфиг sshd уже с ошибкой, сначала исправьте его:")
$output"
    fi
}

# Drop-in files work only if sshd_config includes sshd_config.d (OpenSSH 8.2+)
uses_dropin() {
    [ -d "$CONF_DIR" ] && grep -Eiq '^[[:space:]]*Include[[:space:]]+/etc/ssh/sshd_config\.d/\*' "$MAIN_CONF"
}

dropin_file() { printf '%s/00-sshtunnel-%s.conf' "$CONF_DIR" "$1"; }

sshd_global() {
    { "$SSHD" -T 2>/dev/null || true; } | awk -v k="$1" '$1 == k { sub(/^[^ ]+ /, ""); print }'
}

sshd_for_user() {
    { "$SSHD" -T -C "user=$1,host=localhost,addr=127.0.0.1" 2>/dev/null || true; } | awk -v k="$2" '$1 == k { sub(/^[^ ]+ /, ""); print }'
}

reload_sshd() {
    if command -v systemctl >/dev/null 2>&1 && [ -d /run/systemd/system ]; then
        for unit in ssh.service sshd.service; do
            if systemctl is-active --quiet "$unit"; then
                systemctl reload "$unit" && return 0
            fi
        done
        # Not running (socket activation): the new config is read on the next start
        return 0
    fi
    if command -v rc-service >/dev/null 2>&1; then
        rc-service sshd reload && return 0
    fi
    if command -v service >/dev/null 2>&1; then
        service ssh reload 2>/dev/null && return 0
        service sshd reload 2>/dev/null && return 0
    fi
    for pidfile in /run/sshd.pid /var/run/sshd.pid; do
        if [ -f "$pidfile" ]; then kill -HUP "$(cat "$pidfile")" && return 0; fi
    done
    warn "$(t "could not reload sshd, restart it manually" "не удалось перезагрузить sshd, перезапустите его вручную")"
}

is_managed_user() {
    id -nG "$1" 2>/dev/null | tr ' ' '\n' | grep -qx "$GROUP"
}

managed_users() {
    getent group "$GROUP" | cut -d: -f4 | tr ',' '\n' | grep -v '^$' || true
}

ensure_group() {
    getent group "$GROUP" >/dev/null && return
    if command -v groupadd >/dev/null 2>&1; then groupadd --system "$GROUP"; else addgroup -S "$GROUP"; fi
}

create_user() {
    local user=$1
    if command -v useradd >/dev/null 2>&1; then
        useradd --create-home --shell "$NOLOGIN" --groups "$GROUP" --comment "SSH Tunnel" "$user"
    else
        # BusyBox (Alpine)
        adduser -D -s "$NOLOGIN" -g "SSH Tunnel" "$user"
        addgroup "$user" "$GROUP"
    fi
}

delete_user() {
    local user=$1
    pkill -KILL -u "$user" 2>/dev/null || true
    if command -v userdel >/dev/null 2>&1; then userdel --remove "$user" 2>/dev/null || userdel "$user"; else deluser --remove-home "$user"; fi
}

# ---------- sshd config blocks ----------

remove_block() {
    local user=$1
    rm -f "$(dropin_file "$user")"
    if grep -q "^# BEGIN sshtunnel $user\$" "$MAIN_CONF"; then
        sed -i "/^# BEGIN sshtunnel $user\$/,/^# END sshtunnel $user\$/d" "$MAIN_CONF"
    fi
}

# write_block user file_with_block; rolls back if sshd rejects the result
write_block() {
    local user=$1 block=$2 backup="$WORK_DIR/sshd_config.bak" dropin_backup="$WORK_DIR/dropin.bak" output
    local dropin
    dropin=$(dropin_file "$user")
    cp -p "$MAIN_CONF" "$backup"
    rm -f "$dropin_backup"
    if [ -f "$dropin" ]; then cp -p "$dropin" "$dropin_backup"; fi

    remove_block "$user"
    if uses_dropin; then
        install -m 644 "$block" "$dropin"
    else
        # Make sure the file ends with a newline before appending
        if [ -n "$(tail -c 1 "$MAIN_CONF")" ]; then printf '\n' >>"$MAIN_CONF"; fi
        {
            printf '# BEGIN sshtunnel %s\n' "$user"
            printf '# Keep this block at the end of the file: lines below a Match apply only to it\n'
            cat "$block"
            printf '# END sshtunnel %s\n' "$user"
        } >>"$MAIN_CONF"
    fi

    if ! output=$("$SSHD" -t 2>&1); then
        cp -p "$backup" "$MAIN_CONF"
        rm -f "$dropin"
        if [ -f "$dropin_backup" ]; then cp -p "$dropin_backup" "$dropin"; fi
        die "$(t "sshd rejected the config, changes rolled back:" "sshd не принял конфиг, изменения отменены:")
$output"
    fi
}

# ---------- add ----------

valid_username() { [[ "$1" =~ ^[a-z_][a-z0-9_-]{0,31}$ ]]; }

random_password() {
    head -c 96 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | cut -c1-20
}

# Reads "host:port" items separated by spaces; empty = any destination
ask_permit_open() {
    local answer item dns ok items
    say ""
    say "$(t "Destinations the user may reach through the tunnel." "Куда пользователь сможет подключаться через тоннель.")"
    say "$(t "Empty = anywhere (like a regular ssh -D). Otherwise host:port items separated by spaces," \
             "Пусто = куда угодно (как обычный ssh -D). Иначе пары хост:порт через пробел,")"
    say "$(t "'*' is allowed for the host or port: 10.0.0.5:443 10.0.0.7:* *:80" \
             "'*' можно ставить вместо хоста или порта: 10.0.0.5:443 10.0.0.7:* *:80")"
    say "$(t "Apps connect by IP address, so list IPs (sshd PermitOpen does not support subnets)." \
             "Приложения подключаются по IP-адресам, поэтому указывайте IP (PermitOpen не понимает подсети).")"
    while true; do
        answer=$(ask "$(t "Allowed destinations" "Разрешённые адреса")" "")
        if [ -z "$answer" ]; then printf 'any'; return; fi
        ok=1
        # read -a does not expand globs, so "*:80" stays as typed
        read -r -a items <<<"$answer"
        for item in "${items[@]}"; do
            if ! [[ "$item" =~ ^[A-Za-z0-9.*_-]+:([0-9]{1,5}|\*)$ ]]; then
                warn "$(t "invalid item:" "неверный элемент:") $item"
                ok=0
            fi
        done
        [ "$ok" = 1 ] && break
    done
    answer="${items[*]}"
    # DNS goes through the tunnel as DNS-over-TCP to the DNS server set in the app
    while true; do
        dns=$(ask "$(t "DNS server set in the app (it is allowed on port 53)" "DNS-сервер из настроек приложения (будет разрешён порт 53)")" "$DEFAULT_DNS")
        [[ "$dns" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]] && break
        warn "$(t "enter an IPv4 address" "введите IPv4-адрес")"
    done
    if [ -n "$dns" ] && ! [[ " $answer " == *" $dns:53 "* ]]; then answer="$answer $dns:53"; fi
    printf '%s' "$answer"
}

cmd_add() {
    local user auth uses_key=0 uses_password=0 methods key_mode="" password="" pubkey="" passphrase=""
    local permit_open existing=0 keep_keys=0 auth_label home="" shown_password=""
    title "$(t "New tunnel user" "Новый пользователь для тоннеля")"

    while true; do
        user=$(ask "$(t "User name" "Имя пользователя")" "tunnel")
        if ! valid_username "$user"; then
            warn "$(t "use lowercase latin letters, digits, - and _" "используйте строчные латинские буквы, цифры, - и _")"
            continue
        fi
        if id -u "$user" >/dev/null 2>&1; then
            if is_managed_user "$user"; then
                if confirm "$(t "User $user exists. Change its settings?" "Пользователь $user уже есть. Изменить его настройки?")" y; then
                    existing=1
                    break
                fi
                continue
            fi
            warn "$(t "$user is an existing system user, it will not be changed. Choose another name." \
                      "$user — существующий пользователь системы, его менять нельзя. Выберите другое имя.")"
            continue
        fi
        break
    done

    auth=$(choose "$(t "Login method (same as the Type field in the app)" "Способ входа (как поле «Тип» в приложении)")" \
        "$(t "Private key (recommended)" "Приватный ключ (рекомендуется)")" \
        "$(t "Password" "Пароль")" \
        "$(t "Private key + password (both required)" "Приватный ключ + пароль (нужны оба)")")
    case "$auth" in
        1) uses_key=1; methods="publickey"; auth_label="$(t "Private key" "Приватный ключ")" ;;
        2) uses_password=1; methods="password"; auth_label="$(t "Password" "Пароль")" ;;
        3) uses_key=1; uses_password=1; methods="publickey,password"; auth_label="$(t "Private key + password" "Приватный ключ + пароль")" ;;
    esac

    if [ "$uses_key" = 1 ]; then
        if [ "$existing" = 1 ]; then
            home=$(getent passwd "$user" | cut -d: -f6)
            if [ -s "$home/.ssh/authorized_keys" ] &&
                confirm "$(t "Keep the keys already added for $user?" "Оставить уже добавленные ключи $user?")" y; then
                keep_keys=1
            fi
        fi
        if [ "$keep_keys" = 0 ] || confirm "$(t "Add one more key?" "Добавить ещё один ключ?")" n; then
            key_mode=$(choose "$(t "Key" "Ключ")" \
                "$(t "Generate a new key here (ed25519, shown once)" "Создать новый ключ здесь (ed25519, будет показан один раз)")" \
                "$(t "Paste a public key (ssh-ed25519 / ssh-rsa / ecdsa-...)" "Вставить публичный ключ (ssh-ed25519 / ssh-rsa / ecdsa-...)")")
            if [ "$key_mode" = 1 ]; then
                command -v ssh-keygen >/dev/null 2>&1 || die "$(t "ssh-keygen not found" "не найден ssh-keygen")"
                say "$(t "A passphrase encrypts the key file; enter it in the app's \"Key passphrase\" field. Empty = no passphrase." \
                         "Пароль ключа шифрует файл ключа, в приложении он вводится в поле «Пароль ключа». Пусто — без пароля.")"
                while true; do
                    passphrase=$(ask_secret "$(t "Key passphrase" "Пароль ключа")")
                    [ -z "$passphrase" ] && break
                    if [ "$(ask_secret "$(t "Repeat" "Повторите")")" = "$passphrase" ]; then break; fi
                    warn "$(t "passphrases do not match" "пароли не совпадают")"
                done
            else
                while true; do
                    pubkey=$(ask "$(t "Public key (one line)" "Публичный ключ (одной строкой)")" "")
                    printf '%s\n' "$pubkey" >"$WORK_DIR/check.pub"
                    if [[ "$pubkey" =~ ^(ssh-|ecdsa-|sk-) ]] && ssh-keygen -l -f "$WORK_DIR/check.pub" >/dev/null 2>&1; then break; fi
                    warn "$(t "this is not a valid public key" "это не похоже на публичный ключ")"
                done
            fi
        fi
    fi

    if [ "$uses_password" = 1 ]; then
        if [ "$(choose "$(t "Password" "Пароль")" "$(t "Generate a random password" "Сгенерировать случайный")" "$(t "Enter a password" "Ввести самому")")" = 1 ]; then
            password=$(random_password)
            shown_password=$password
        else
            while true; do
                password=$(ask_secret "$(t "Password (8+ characters)" "Пароль (от 8 символов)")")
                if [ "${#password}" -lt 8 ]; then warn "$(t "too short" "слишком короткий")"; continue; fi
                if [ "$(ask_secret "$(t "Repeat" "Повторите")")" = "$password" ]; then break; fi
                warn "$(t "passwords do not match" "пароли не совпадают")"
            done
        fi
    fi

    permit_open=$(ask_permit_open)

    title "$(t "Summary" "Итого")"
    field "$(t "User" "Пользователь")" "$user$([ "$existing" = 1 ] && t " (existing)" " (существующий)")"
    field "$(t "Login" "Вход")" "$auth_label"
    field "$(t "Destinations" "Адреса")" "$([ "$permit_open" = any ] && t "any" "любые" || printf '%s' "$permit_open")"
    field "$(t "Not allowed" "Запрещено")" "$(t "shell, commands, SFTP/SCP, remote forwarding, agent, X11, tun" "оболочка, команды, SFTP/SCP, обратный проброс, агент, X11, tun")"
    confirm "$(t "Apply?" "Применить?")" y || { say "$(t "Cancelled." "Отменено.")"; return; }

    # --- apply ---
    ensure_group
    if [ "$existing" = 0 ]; then
        create_user "$user"
        info "$(t "user created:" "пользователь создан:") $user"
    fi
    home=$(getent passwd "$user" | cut -d: -f6)
    if [ -z "$home" ] || [ ! -d "$home" ]; then
        die "$(t "home directory not found for" "не найден домашний каталог") $user"
    fi

    if [ "$uses_password" = 1 ]; then
        printf '%s:%s\n' "$user" "$password" | chpasswd
    else
        # "*" = no password, but the account stays unlocked (sshd without PAM refuses locked accounts)
        printf '%s:*\n' "$user" | chpasswd -e
    fi

    local keys="$home/.ssh/authorized_keys" key_line=""
    if [ "$key_mode" = 1 ]; then
        ssh-keygen -q -t ed25519 -N "$passphrase" -C "sshtunnel-$user@$(hostname)" -f "$WORK_DIR/key"
        key_line=$(cat "$WORK_DIR/key.pub")
    elif [ "$key_mode" = 2 ]; then
        key_line=$pubkey
    fi
    if [ "$uses_key" = 1 ]; then
        install -d -m 700 -o "$user" -g "$(id -gn "$user")" "$home/.ssh"
        if [ "$keep_keys" = 0 ]; then : >"$keys"; fi
        # restrict = no pty, agent, X11, user rc; only port forwarding is re-enabled
        if [ -n "$key_line" ]; then printf 'restrict,port-forwarding %s\n' "$key_line" >>"$keys"; fi
        chown "$user:$(id -gn "$user")" "$keys"
        chmod 600 "$keys"
    else
        rm -f "$keys"
    fi

    local block="$WORK_DIR/block.conf"
    {
        printf '# SSH Tunnel user %s: TCP forwarding only (ssh -D). Managed by sshtunnel-user.sh\n' "$user"
        printf 'Match User %s\n' "$user"
        printf '    AuthenticationMethods %s\n' "$methods"
        [ "$uses_key" = 1 ] && printf '    PubkeyAuthentication yes\n'
        [ "$uses_password" = 1 ] && printf '    PasswordAuthentication yes\n'
        printf '    AllowTcpForwarding local\n'
        printf '    PermitOpen %s\n' "$permit_open"
        printf '    GatewayPorts no\n'
        printf '    AllowStreamLocalForwarding no\n'
        printf '    AllowAgentForwarding no\n'
        printf '    X11Forwarding no\n'
        printf '    PermitTunnel no\n'
        printf '    PermitTTY no\n'
        printf '    PermitUserRC no\n'
        printf '    ForceCommand %s\n' "$NOLOGIN"
    } >"$block"
    write_block "$user" "$block"
    reload_sshd
    info "$(t "sshd config updated" "конфиг sshd обновлён")$(uses_dropin && printf ': %s' "$(dropin_file "$user")" || printf ': %s' "$MAIN_CONF")"

    check_access "$user"
    print_result "$user" "$auth_label" "$shown_password" "$key_mode" "$passphrase"
}

# Warns about global settings that would still block the user
check_access() {
    local user=$1 value
    value=$(sshd_for_user "$user" allowtcpforwarding)
    if [ -n "$value" ] && [ "$value" != local ]; then
        warn "$(t "AllowTcpForwarding for $user is \"$value\": another Match block overrides ours" \
                  "AllowTcpForwarding для $user = \"$value\": наш блок перекрыт другим Match")"
    fi
    for key in allowusers allowgroups; do
        value=$(sshd_global "$key")
        if [ -n "$value" ]; then
            warn "$(t "sshd has ${key}: $value — add $user (or group $GROUP) there, otherwise the login is refused" \
                      "в sshd задан ${key}: $value — добавьте туда $user (или группу $GROUP), иначе вход будет запрещён")"
        fi
    done
    for key in denyusers denygroups; do
        value=$(sshd_global "$key")
        if [ -n "$value" ]; then
            warn "$(t "sshd has ${key}: $value — make sure it does not match $user" "в sshd задан ${key}: $value — проверьте, что он не касается $user")"
        fi
    done
}

print_result() {
    local user=$1 auth_label=$2 password=$3 key_mode=$4 passphrase=$5 ports addresses
    ports=$(sshd_global port | tr '\n' ' ' | sed 's/ $//')
    # IPv4 only: the app connects over IPv4
    addresses=$( (hostname -I 2>/dev/null || true) | tr ' ' '\n' | grep -E '^[0-9.]+$' | tr '\n' ' ' | sed 's/ $//' || true)

    title "$(t "Done. Settings for the SSH Tunnel app (Servers → Add Server):" "Готово. Настройки для приложения SSH Tunnel (Серверы → Добавить сервер):")"
    field "$(t "Host" "Хост")" "${addresses:-$(hostname)}  $(t "(the address reachable from the device)" "(адрес, доступный с устройства)")"
    field "$(t "Port" "Порт")" "${ports:-22}"
    field "$(t "User" "Пользователь")" "$user"
    field "$(t "Type" "Тип")" "$auth_label"
    # Only a generated password is shown: a typed one is already known
    if [ -n "$password" ]; then field "$(t "Password" "Пароль")" "$password"; fi

    if [ "$key_mode" = 1 ]; then
        title "$(t "Private key — paste it into the \"Private key\" field. It is not stored on this server:" \
                   "Приватный ключ — вставьте его в поле «Приватный ключ». На сервере он не сохраняется:")"
        cat "$WORK_DIR/key" >&2
        if [ -n "$passphrase" ]; then say "$(t "The key is protected by the passphrase you entered." "Ключ защищён введённым паролем ключа.")"; fi
        if confirm "$(t "Also save the private key to /root for copying as a file?" "Сохранить приватный ключ ещё и в файл в /root, чтобы скопировать его файлом?")" n; then
            install -m 600 "$WORK_DIR/key" "/root/sshtunnel-$user-ed25519"
            info "/root/sshtunnel-$user-ed25519 — $(t "delete it after copying" "удалите после копирования")"
        fi
    fi

    title "$(t "Check from a computer:" "Проверка с компьютера:")"
    say "  ssh -N -D 1080 -p ${ports%% *} $user@${addresses%% *}"
    say "  curl --socks5-hostname 127.0.0.1:1080 https://ifconfig.me"
    say "$(t "A plain \"ssh $user@...\" login is refused: this account has no shell." \
             "Обычный вход \"ssh $user@...\" будет отклонён: у этой учётной записи нет оболочки.")"
}

# ---------- remove / list ----------

cmd_list() {
    local users user methods
    users=$(managed_users)
    title "$(t "Tunnel users (group $GROUP):" "Пользователи тоннеля (группа $GROUP):")"
    if [ -z "$users" ]; then
        say "  $(t "none" "нет")"
        return
    fi
    for user in $users; do
        methods=$(sshd_for_user "$user" authenticationmethods)
        say "  $user  ($(t "login" "вход"): ${methods:-?}, $(t "destinations" "адреса"): $(sshd_for_user "$user" permitopen))"
    done
}

cmd_remove() {
    local user users
    users=$(managed_users)
    if [ -z "$users" ]; then
        say "$(t "No tunnel users." "Пользователей тоннеля нет.")"
        return
    fi
    cmd_list
    user=$(ask "$(t "User to remove" "Какого пользователя удалить")" "")
    [ -n "$user" ] || return
    if ! valid_username "$user" || ! is_managed_user "$user"; then
        die "$(t "$user is not a tunnel user" "$user не пользователь тоннеля")"
    fi
    confirm "$(t "Remove $user, its home directory and sshd settings? Active tunnels will be closed." \
                 "Удалить $user, его домашний каталог и настройки sshd? Активные тоннели будут закрыты.")" n || return

    local backup="$WORK_DIR/sshd_config.bak" output
    cp -p "$MAIN_CONF" "$backup"
    remove_block "$user"
    if ! output=$("$SSHD" -t 2>&1); then
        cp -p "$backup" "$MAIN_CONF"
        die "$(t "sshd rejected the config, changes rolled back:" "sshd не принял конфиг, изменения отменены:")
$output"
    fi
    reload_sshd
    delete_user "$user"
    info "$(t "removed:" "удалён:") $user"
}

# ---------- main ----------

main() {
    # "list" asks nothing, so it also works without a terminal
    if [ "${1:-}" != list ]; then
        open_tty
        choose_language
    fi
    check_system
    WORK_DIR=$(mktemp -d)
    chmod 700 "$WORK_DIR"

    case "${1:-}" in
        add) cmd_add; return ;;
        remove) cmd_remove; return ;;
        list) cmd_list; return ;;
        "") ;;
        *) die "$(t "unknown command: $1 (add, remove, list)" "неизвестная команда: $1 (add, remove, list)")" ;;
    esac

    while true; do
        case "$(choose "SSH Tunnel: $(t "tunnel-only SSH users" "пользователи SSH только для тоннеля")" \
            "$(t "Add or change a user" "Добавить или изменить пользователя")" \
            "$(t "Remove a user" "Удалить пользователя")" \
            "$(t "List users" "Список пользователей")" \
            "$(t "Exit" "Выход")")" in
            1) cmd_add ;;
            2) cmd_remove ;;
            3) cmd_list ;;
            4) return ;;
        esac
    done
}

main "$@"
