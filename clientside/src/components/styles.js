const ios = require('./iosTheme');

let styles = {
    // -----------------------------------------------------------------------
    // Typography
    // -----------------------------------------------------------------------
    titleText: {
        fontFamily: ios.typography.fontFamilyDisplay,
        fontWeight: ios.typography.title3.fontWeight,
        fontSize: ios.typography.title3.fontSize,
        color: ios.colors.label,
        letterSpacing: ios.typography.title3.letterSpacing,
    },

    // -----------------------------------------------------------------------
    // Dashboard
    // -----------------------------------------------------------------------
    dashboardChartPanel: {
        height: 300,
        width: '45%',
        margin: ios.spacing.sm,
        position: 'relative',
        float: 'left',
        borderRadius: ios.radii.lg,
        boxShadow: ios.shadows.card,
        backgroundColor: ios.colors.systemBackground,
    },

    // -----------------------------------------------------------------------
    // Toolbar
    // -----------------------------------------------------------------------
    moduleToolBarButtons: {
        marginLeft: ios.spacing.sm,
        borderRadius: ios.radii.md,
        fontFamily: ios.typography.fontFamily,
    },

    // -----------------------------------------------------------------------
    // Dialogs
    // -----------------------------------------------------------------------
    formDialog: {
        width: 500,
        height: 398,
        borderRadius: ios.radii.xl,
        boxShadow: ios.shadows.modal,
    },
    formDialogLargeWidth: {
        width: 800,
        height: 398,
        borderRadius: ios.radii.xl,
        boxShadow: ios.shadows.modal,
    },
    moreTableContentDialogLargeWidth: {
        width: 1024,
        height: 550,
        borderRadius: ios.radii.xl,
        boxShadow: ios.shadows.modal,
    },
    formDialogContainer: {
        padding: `${ios.spacing.lg}px ${ios.spacing.lg}px ${ios.spacing.md}px ${ios.spacing.md}px`,
        width: '400px',
        fontFamily: ios.typography.fontFamily,
    },
    formDialogFields: {
        width: 280,
        padding: ios.spacing.xs,
        borderRadius: ios.radii.sm,
        fontFamily: ios.typography.fontFamily,
    },
    formDialogFieldsTexField: {
        width: 280,
        height: 100,
        padding: ios.spacing.xs,
        borderRadius: ios.radii.sm,
        fontFamily: ios.typography.fontFamily,
    },

    // -----------------------------------------------------------------------
    // Expander rows
    // -----------------------------------------------------------------------
    expanderRow: {
        padding: `${ios.spacing.lg}px ${ios.spacing.lg}px ${ios.spacing.md}px ${ios.spacing.md}px`,
        backgroundColor: ios.colors.secondarySystemBackground,
        borderRadius: ios.radii.md,
    },
    expanderRowHighlight: {
        fontWeight: ios.typography.headline.fontWeight,
        margin: ios.spacing.xs,
        color: ios.colors.blue,
    },

    // -----------------------------------------------------------------------
    // Button spacing in large dialogs
    // -----------------------------------------------------------------------
    formDialogLargeWidthAddButtons: {
        marginLeft: 0,
        marginTop: ios.spacing.sm,
        marginRight: ios.spacing.sm,
        marginBottom: ios.spacing.sm,
        borderRadius: ios.radii.pill,
        backgroundColor: ios.colors.blue,
        color: ios.colors.white,
    },

    // -----------------------------------------------------------------------
    // Number presentations
    // -----------------------------------------------------------------------
    numberPresentationGreenBold: {
        color: ios.colors.green,
        fontWeight: ios.typography.headline.fontWeight,
        fontSize: ios.typography.body.fontSize,
        fontFamily: ios.typography.fontFamily,
    },
    numberPresentationGreen: {
        color: ios.colors.green,
        fontWeight: ios.typography.headline.fontWeight,
        fontFamily: ios.typography.fontFamily,
    },
    numberPresentationRed: {
        color: ios.colors.red,
        fontWeight: ios.typography.headline.fontWeight,
        fontFamily: ios.typography.fontFamily,
    },

    // -----------------------------------------------------------------------
    // Code / log block
    // -----------------------------------------------------------------------
    commonBlockText: {
        width: 400,
        margin: ios.spacing.sm,
        overflow: 'scroll',
        padding: ios.spacing.md,
        backgroundColor: ios.colors.systemGray6,
        border: `1px solid ${ios.colors.separator}`,
        borderRadius: ios.radii.md,
        fontFamily: '"SF Mono", "Menlo", "Monaco", "Courier New", monospace',
        fontSize: ios.typography.footnote.fontSize,
        color: ios.colors.label,
    },

    // -----------------------------------------------------------------------
    // iOS card (reusable surface)
    // -----------------------------------------------------------------------
    card: {
        backgroundColor: ios.colors.systemBackground,
        borderRadius: ios.radii.xl,
        boxShadow: ios.shadows.card,
        padding: ios.spacing.lg,
        marginBottom: ios.spacing.md,
    },

    // -----------------------------------------------------------------------
    // iOS-style pill button
    // -----------------------------------------------------------------------
    pillButton: {
        backgroundColor: ios.colors.blue,
        color: ios.colors.white,
        borderRadius: ios.radii.pill,
        padding: `${ios.spacing.sm}px ${ios.spacing.lg}px`,
        border: 'none',
        fontFamily: ios.typography.fontFamily,
        fontWeight: ios.typography.headline.fontWeight,
        fontSize: ios.typography.body.fontSize,
        cursor: 'pointer',
    },

    // -----------------------------------------------------------------------
    // Status badge
    // -----------------------------------------------------------------------
    badgeActive: {
        backgroundColor: ios.colors.green,
        color: ios.colors.white,
        borderRadius: ios.radii.pill,
        padding: `${ios.spacing.xs / 2}px ${ios.spacing.sm}px`,
        fontSize: ios.typography.caption1.fontSize,
        fontWeight: ios.typography.headline.fontWeight,
        display: 'inline-block',
    },
    badgeInactive: {
        backgroundColor: ios.colors.systemGray,
        color: ios.colors.white,
        borderRadius: ios.radii.pill,
        padding: `${ios.spacing.xs / 2}px ${ios.spacing.sm}px`,
        fontSize: ios.typography.caption1.fontSize,
        fontWeight: ios.typography.headline.fontWeight,
        display: 'inline-block',
    },
    badgePending: {
        backgroundColor: ios.colors.orange,
        color: ios.colors.white,
        borderRadius: ios.radii.pill,
        padding: `${ios.spacing.xs / 2}px ${ios.spacing.sm}px`,
        fontSize: ios.typography.caption1.fontSize,
        fontWeight: ios.typography.headline.fontWeight,
        display: 'inline-block',
    },
};

module.exports = styles;