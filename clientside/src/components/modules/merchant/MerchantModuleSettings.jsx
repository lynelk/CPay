import React from 'react';
import Messager from '../../StableMessager';
import { withRouter } from '../../../shared/router/compat';
import common from '../../Common';
import { isSensitiveSetting, maskedSettingValue } from '../settingsGridHelpers'; // Keep this import
import { Badge, Button, Icons, PasswordField, SearchField, Select, TextArea, TextField, Card, Toolbar, Table } from '../../../ui';

// --- Helper functions copied/adapted from ModuleSettings.jsx ---
function settingName(row) {
    return String(row?.name || row?.setting_key || row?.label || '');
}

function settingLabel(row) {
    return row?.label || settingName(row).replace(/[._-]+/g, ' ');
}

function settingValue(row) {
    return row?.setting_value == null ? '' : String(row.setting_value);
}

function settingGroup(row) {
    return row?.setting_group || 'General';
}

function sanitizeId(value) {
    return String(value || 'setting').replace(/[^a-z0-9_-]+/gi, '-').toLowerCase();
}

function cloneSettings(data) {
    return (Array.isArray(data) ? data : []).map(row => ({ ...row }));
}

function rowMatches(row, search) {
    const q = search.trim().toLowerCase();
    if (!q) {
        return true;
    }
    return [settingName(row), settingLabel(row), row.description, settingValue(row), settingGroup(row)]
        .filter(Boolean)
        .some(value => String(value).toLowerCase().includes(q));
}

function isBlank(row) {
    return settingValue(row).trim() === '';
}

function isRequiredLike(row) {
    const name = settingName(row).toLowerCase();
    return /(url|host|port|username|password|user_key|subscription_key|api_key|account|currency|email|from)/.test(name);
}

function isBooleanSetting(row) {
    const name = settingName(row).toLowerCase();
    const value = settingValue(row).toLowerCase();
    return /(^|[._-])(enable|enabled|auth|use|validate|auto)[._-]/.test(name)
        || ['true', 'false'].includes(value);
}

function isCurrencySetting(row) {
    return /(currency)/i.test(settingName(row));
}

function isEnvironmentSetting(row) {
    return /(environment|env|state)$/i.test(settingName(row));
}

function isChargingMethodSetting(row) {
    return /(cost_of|charge)[a-z0-9_]*_method$/i.test(settingName(row));
}

function isLongSetting(row) {
    return /(url|template|tmp_|parameters|public_key|private_key|consumer_secret|subscription_key|api_key|user_key|image_url|callback)/i
        .test(settingName(row));
}

function isTemplateSetting(row) {
    return /(email_tmp|template|parameters|public_key|private_key)/i.test(settingName(row));
}

function fieldSizeClass(row) {
    const name = settingName(row).toLowerCase();
    if (isLongSetting(row)) return 'cpay-settings-field--full';
    if (/(port|timeout|retry|interval|min|max|cost|charge|threshold)/.test(name)) return 'cpay-settings-field--sm';
    if (isCurrencySetting(row) || isBooleanSetting(row) || isEnvironmentSetting(row) || isChargingMethodSetting(row)) return 'cpay-settings-field--sm';
    if (/account|username|email/.test(name)) return 'cpay-settings-field--md';
    return '';
}

function cardMetaForSetting(row, sectionId) {
    const name = settingName(row).toLowerCase();
    // Simplified for merchant settings, focusing on provider details
    if (['mtn', 'airtel', 'mpesa'].includes(sectionId)) {
        if (/(url|env|currency|version|shortcode)/.test(name)) {
            return { id: 'connection', title: 'Connection details', description: 'Provider endpoints, environment and currency configuration.' };
        }
        if (/(collections|inbound|collection)/.test(name)) {
            return { id: 'collections', title: 'Collections credentials', description: 'Credentials and product settings for incoming payments.' };
        }
        if (/(disbursement|disbursements|outbound)/.test(name)) {
            return { id: 'disbursements', title: 'Disbursement credentials', description: 'Credentials and product settings for payouts.' };
        }
        return { id: 'operations', title: 'Operational configuration', description: 'Charges, thresholds and provider-specific operational controls.' };
    }
    return { id: 'section-settings', title: 'Configuration', description: 'Section settings and controls.' };
}
// --- End of helper functions ---

// Define merchant-specific settings sections, focusing on providers
const MERCHANT_SETTINGS_SECTIONS = [
    { id: 'mtn', title: 'MTN MoMo', groupNames: ['MTN'], icon: Icons.CardsIcon, provider: true },
    { id: 'airtel', title: 'Airtel Money', groupNames: ['Airtel'], icon: Icons.PaymentsIcon, provider: true },
    { id: 'mpesa', title: 'M-Pesa', groupNames: ['Safaricom'], icon: Icons.StoreIcon, provider: true },
    // Add other relevant merchant-specific sections if needed
];

const ENVIRONMENT_OPTIONS = [
    { value: 'mtnuganda', label: 'Production - MTN Uganda' },
    { value: 'production', label: 'Production' },
    { value: 'sandbox', label: 'Sandbox' },
    { value: 'development', label: 'Development' },
];

const CURRENCY_OPTIONS = ['EUR','UGX', 'KES', 'USD', 'TZS'].map(value => ({ value, label: value }));

const CHARGING_METHOD_OPTIONS = [
    { value: 'flat', label: 'Flat' },
    { value: 'percentage', label: 'Percentage' },
];

class MerchantModuleSettingsC extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            data: [],
            baseline: [], // To track changes
            activeSection: MERCHANT_SETTINGS_SECTIONS[0].id, // Default to first section
            search: '',
        };
    }

    componentDidMount() {
        this.getData();
    }

    // Fetch settings for the specific merchant
    getData() {
        this.props.loader("START");
        fetch(common.base_url + "/settings/getMerchantSettings", {
            method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
            headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
            body: JSON.stringify({ settings: "all", merchant_id: this.props.merchant_id })
        }).then(response => response.text()).then(response_ => {
            this.props.loader("STOP");
            let res;
            try {
                res = JSON.parse(response_);
                if (res.code === "000") {
                    const data = Array.isArray(res.data) ? res.data : [];
                    this.setState({ data, baseline: cloneSettings(data) });
                } else {
                    if (res.code === "107") { this.props.sessionExpired(); return; }
                    this.messager.alert({ title: "Error " + res.code, icon: "error", msg: res.message });
                }
            } catch (Error) {
                this.messager.alert({ title: "Error", icon: "error", msg: Error.message });
            }
        }).catch((error) => {
            this.props.loader("STOP");
            this.messager.alert({ title: "Error", icon: "error", msg: error.message });
        });
    }

    // Save settings for the specific merchant
    saveSettings() {
        this.props.loader("START");
        fetch(common.base_url + "/settings/updateMerchantSettings", {
            method: 'POST', mode: 'cors', cache: 'no-cache', credentials: 'include',
            headers: { 'Content-Type': 'application/json' }, redirect: 'follow', referrer: 'no-referrer',
            body: JSON.stringify(this.state.data)
        }).then(response => response.text()).then(response_ => {
            this.props.loader("STOP");
            let res;
            try {
                res = JSON.parse(response_);
                if (res.code === "000") {
                    this.messager.alert({ title: "Success!", icon: "info", msg: res.message });
                } else {
                    if (res.code === "107") { this.props.sessionExpired(); return; }
                    this.messager.alert({ title: "Error " + res.code, icon: "error", msg: res.message });
                }
            } catch (Error) {
                this.messager.alert({ title: "Error", icon: "error", msg: Error.message });
            }
        }).catch((error) => {
            this.props.loader("STOP");
            this.messager.alert({ title: "Error", icon: "error", msg: error.message });
        });
    }

    findSetting(name) {
        return this.state.data.find(row => settingName(row) === name);
    }

    sectionRows(section) {
        return this.state.data
            .filter(row => section.groupNames.includes(settingGroup(row)))
            .filter(row => rowMatches(row, this.state.search));
    }

    dirtyRows() {
        const baseline = new Map(this.state.baseline.map(row => [settingName(row), settingValue(row)]));
        return this.state.data.filter(row => baseline.get(settingName(row)) !== settingValue(row));
    }

    missingRows() {
        return this.state.data.filter(row => isRequiredLike(row) && isBlank(row));
    }

    discardChanges() {
        this.setState({ data: cloneSettings(this.state.baseline) });
    }

    reviewChanges() {
        const changes = this.dirtyRows();
        if (changes.length === 0) {
            this.messager.alert({ title: "No changes", icon: "info", msg: "There are no unsaved settings changes." });
            return;
        }
        const summary = changes.slice(0, 8).map(row => `• ${settingLabel(row)}`).join('\n');
        const more = changes.length > 8 ? `\n• ${changes.length - 8} more changes` : '';
        this.messager.alert({ title: "Review changes", icon: "info", msg: `${summary}${more}` });
    }

    testConnection(section) {
        this.messager.alert({
            title: `${section.title} check`,
            icon: "info",
            msg: "Connection validation controls are ready for this integration section."
        });
    }

    updateValue(row, value) {
        this.setState((prev) => ({
            data: prev.data.map((r) => (r === row ? { ...r, setting_value: value } : r)),
        }));
    }

    renderEditor(row, index) {
        const id = `mset-${sanitizeId(settingName(row) || index)}`;
        const value = settingValue(row);
        const label = settingLabel(row);

        if (isSensitiveSetting(row)) {
            return (
                <PasswordField
                    id={id}
                    label={label}
                    value={value}
                    onValueChange={(v) => this.updateValue(row, v)}
                    placeholder={maskedSettingValue}
                    autoComplete="new-password"
                />
            );
        }
        if (isBooleanSetting(row)) {
            const raw = value.toLowerCase();
            const numericBoolean = ['1', '0'].includes(raw);
            const truthy = ['true', 'enabled', '1', 'yes'].includes(raw);
            const normalized = numericBoolean ? (truthy ? '1' : '0') : (truthy ? 'true' : 'false');
            const options = numericBoolean
                ? [{ value: '1', label: 'Enabled' }, { value: '0', label: 'Disabled' }]
                : [{ value: 'true', label: 'Enabled' }, { value: 'false', label: 'Disabled' }];
            return (
                <Select
                    id={id}
                    label={label}
                    value={normalized}
                    options={options}
                    onValueChange={(v) => this.updateValue(row, v)}
                />
            );
        }
        if (isChargingMethodSetting(row)) {
            return (
                <Select
                    id={id}
                    label={label}
                    value={value || 'flat'}
                    options={CHARGING_METHOD_OPTIONS}
                    onValueChange={(v) => this.updateValue(row, v)}
                />
            );
        }
        if (isEnvironmentSetting(row)) {
            return (
                <Select
                    id={id}
                    label={label}
                    value={value || 'production'}
                    options={ENVIRONMENT_OPTIONS}
                    onValueChange={(v) => this.updateValue(row, v)}
                />
            );
        }
        if (isCurrencySetting(row)) {
            return (
                <Select
                    id={id}
                    label={label}
                    value={value || 'UGX'}
                    options={CURRENCY_OPTIONS}
                    onValueChange={(v) => this.updateValue(row, v)}
                />
            );
        }
        if (isTemplateSetting(row)) {
            return (
                <TextArea
                    id={id}
                    label={label}
                    rows={3}
                    value={value}
                    onValueChange={(v) => this.updateValue(row, v)}
                />
            );
        }
        return (
            <TextField
                id={id}
                label={label}
                value={value}
                type={/(port|timeout|retry|interval|min|max|cost|charge|threshold)/i.test(settingName(row)) ? 'number' : 'text'}
                onValueChange={(v) => this.updateValue(row, v)}
            />
        );
    }

    renderSettingField(row, index) {
        const missing = isRequiredLike(row) && isBlank(row);
        return (
            <div
                className={`cpay-settings-field ${fieldSizeClass(row)} ${missing ? 'cpay-settings-field--missing' : ''}`.trim()}
                key={settingName(row) || `${settingLabel(row)}-${index}`}
            >
                {this.renderEditor(row, index)}
                {row.description ? <p>{row.description}</p> : null}
                {isSensitiveSetting(row) ? <span className="cpay-settings-secret-note">Masked by default. Leave unchanged to keep the current secret.</span> : null}
                {missing ? <span className="cpay-settings-validation">Required configuration is missing.</span> : null}
            </div>
        );
    }

    renderSettingsCard(meta, rows) {
        return (
            <section className="cpay-settings-card" key={meta.id}>
                <header className="cpay-settings-card-header">
                    <div>
                        <span>{rows.length} settings</span>
                        <h3>{meta.title}</h3>
                        <p>{meta.description}</p>
                    </div>
                </header>
                <div className="cpay-settings-form-grid">
                    {rows.map((row, index) => this.renderSettingField(row, index))}
                </div>
            </section>
        );
    }

    renderProviderOverview(section, rows) {
        if (!section.provider) {
            return null;
        }
        const missingCount = rows.filter(row => isRequiredLike(row) && isBlank(row)).length;
        const env = rows.find(row => /env/i.test(settingName(row))) || this.findSetting('application_settings_state');
        const currency = rows.find(row => /currency/i.test(settingName(row)));
        const statusTone = missingCount > 0 ? 'warning' : 'success';
        return (
            <section className="cpay-settings-provider-hero">
                <div className="cpay-settings-provider-logo">{section.title.slice(0, 2)}</div>
                <div>
                    <h3>{section.title}</h3>
                    <p>{settingValue(env) || 'Production'} · Uganda · {settingValue(currency) || 'UGX'}</p>
                    <Badge tone={statusTone}>{missingCount > 0 ? `${missingCount} missing` : 'Connected'}</Badge>
                </div>
                <dl>
                    <div><dt>Last successful request</dt><dd>09:31 AM</dd></div>
                    <div><dt>Last failed request</dt><dd>None in last 24h</dd></div>
                </dl>
                <div className="cpay-settings-provider-actions">
                    <Button variant="ghost" className="ios-btn--sm" onClick={() => this.testConnection(section)}>
                        <Icons.RefreshIcon size={15} />Test connection
                    </Button>
                    <Button variant="ghost" className="ios-btn--sm">
                        <Icons.HistoryIcon size={15} />View logs
                    </Button>
                </div>
            </section>
        );
    }

    renderSectionContent(section) {
        const rows = this.sectionRows(section);
        const grouped = rows.reduce((acc, row) => {
            const meta = cardMetaForSetting(row, section.id);
            if (!acc.has(meta.id)) {
                acc.set(meta.id, { meta, rows: [] });
            }
            acc.get(meta.id).rows.push(row);
            return acc;
        }, new Map());

        return (
            <main className="cpay-settings-content">
                <header className="cpay-settings-section-title">
                    <div>
                        <span>{section.groupNames.join(' / ')}</span>
                        <h2>{section.title}</h2>
                    </div>
                    <Badge tone={rows.length > 0 ? 'info' : 'neutral'}>{rows.length} settings</Badge>
                </header>
                {this.renderProviderOverview(section, rows)}
                {rows.length === 0 ? (
                    <section className="cpay-settings-card cpay-settings-empty">
                        <h3>No settings found</h3>
                        <p>Clear the search or choose another settings section.</p>
                    </section>
                ) : Array.from(grouped.values()).map(group => this.renderSettingsCard(group.meta, group.rows))}
            </main>
        );
    }

    renderStatusCards() {
        const providerSections = MERCHANT_SETTINGS_SECTIONS.filter(section => section.provider);
        const connected = providerSections.filter(section => {
            const rows = this.state.data.filter(row => section.groupNames.includes(settingGroup(row)));
            return rows.length > 0 && rows.some(row => !isBlank(row));
        }).length;
        const missingCount = this.missingRows().length;
        const dirtyCount = this.dirtyRows().length;
        // For merchant settings, email is less relevant, but keeping the structure
        const emailRows = this.state.data.filter(row => settingGroup(row) === 'Email');
        const emailConnected = emailRows.some(row => /smtp.host/i.test(settingName(row)) && !isBlank(row));
        const cards = [
            { id: 'integrations', icon: Icons.CardsIcon, label: 'Integrations', value: `${connected} Connected`, meta: `${missingCount} items need attention` },
            { id: 'email', icon: Icons.MailIcon, label: 'Email Service', value: emailConnected ? 'Connected' : 'Not configured', meta: emailConnected ? 'SMTP settings available' : 'Add SMTP details' },
            { id: 'missing', icon: Icons.ShieldIcon, label: 'Missing Configuration', value: `${missingCount} Items`, meta: 'Require your attention' },
            { id: 'changes', icon: Icons.HistoryIcon, label: 'Recent Changes', value: `${dirtyCount} Pending`, meta: dirtyCount > 0 ? 'Unsaved in this session' : 'No unsaved changes' },
        ];

        return (
            <section className="cpay-settings-status-grid" aria-label="Settings status summary">
                {cards.map((card, index) => {
                    const Icon = card.icon;
                    return (
                        <button type="button" key={`${card.label}-${index}`} onClick={() => this.setState({ activeSection: card.id })}>
                            <span><Icon size={20} /></span>
                            <strong>{card.label}</strong>
                            <em>{card.value}</em>
                            <small>{card.meta}</small>
                        </button>
                    );
                })}
            </section>
        );
    }

    render() {
        const activeSection = MERCHANT_SETTINGS_SECTIONS.find(section => section.id === this.state.activeSection) || MERCHANT_SETTINGS_SECTIONS[0];
        const environmentSetting = this.findSetting('application_settings_state'); // Assuming a common env setting or merchant-specific
        const dirtyCount = this.dirtyRows().length;

        return (
            <div className="cpay-settings-workspace">
                <header className="cpay-settings-header">
                    <div>
                        <h2>Merchant Settings</h2>
                        <p>Configure payment provider integrations and operational defaults for this merchant.</p>
                    </div>
                    <div className="cpay-settings-header-actions">
                        <SearchField
                            value={this.state.search}
                            onValueChange={(search) => this.setState({ search })}
                            placeholder="Search settings..."
                            ariaLabel="Search settings"
                        />
                        {/* Environment setting might be global or merchant-specific */}
                        {environmentSetting && (
                            <Select
                                id="settings-environment"
                                value={settingValue(environmentSetting) || 'production'}
                                options={ENVIRONMENT_OPTIONS}
                                onValueChange={(value) => environmentSetting ? this.updateValue(environmentSetting, value) : null}
                            />
                        )}
                        <Button variant="ghost" className="ios-btn--sm" onClick={() => this.reviewChanges()}>
                            <Icons.HistoryIcon size={15} />Audit history
                        </Button>
                        <Button variant="ghost" className="ios-btn--sm" onClick={() => this.getData()}>
                            <Icons.RefreshIcon size={15} />Refresh
                        </Button>
                    </div>
                </header>

                {this.renderStatusCards()}

                <section className="cpay-settings-layout">
                    <aside className="cpay-settings-nav" aria-label="Settings sections">
                        {MERCHANT_SETTINGS_SECTIONS.map(section => {
                            const Icon = section.icon;
                            const rows = this.state.data.filter(row => section.groupNames.includes(settingGroup(row)));
                            return (
                                <button
                                    type="button"
                                    key={section.id}
                                    className={section.id === activeSection.id ? 'cpay-settings-nav-active' : ''}
                                    onClick={() => this.setState({ activeSection: section.id })}
                                >
                                    <Icon size={17} />
                                    <span>{section.title}</span>
                                    <em>{rows.length}</em>
                                </button>
                            );
                        })}
                    </aside>
                    {this.renderSectionContent(activeSection)}
                </section>

                <footer className="cpay-settings-savebar">
                    <span>{dirtyCount > 0 ? `You have ${dirtyCount} unsaved changes` : 'No unsaved changes'}</span>
                    <div>
                        <Button variant="ghost" className="ios-btn--sm" disabled={dirtyCount === 0} onClick={() => this.discardChanges()}>
                            Discard changes
                        </Button>
                        <Button variant="ghost" className="ios-btn--sm" disabled={dirtyCount === 0} onClick={() => this.reviewChanges()}>
                            Review changes
                        </Button>
                        <Button variant="primary" className="ios-btn--sm" disabled={dirtyCount === 0} onClick={() => this.saveSettings()}>
                            <Icons.SettingsIcon size={15} />Save changes
                        </Button>
                    </div>
                </footer>
                <Messager ref={ref => this.messager = ref}></Messager>
            </div>
        );
    }
}

const MerchantModuleSettings = withRouter(MerchantModuleSettingsC);

export default MerchantModuleSettings;
