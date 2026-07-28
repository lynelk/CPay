export function replaceSmsColumnToken(content, columnName, value) {
    const source = content == null ? '' : String(content);
    const token = `{${String(columnName).toLowerCase()}}`;
    const index = source.toLowerCase().indexOf(token);

    if (index === -1) {
        return source;
    }

    const replacement = value == null ? '' : String(value);
    return `${source.slice(0, index)}${replacement}${source.slice(index + token.length)}`;
}
