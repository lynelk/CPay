/**
 * Tailwind CSS className strings for CPay components.
 * Usage: className={styles.X}
 *
 * For rc-easyui components requiring pixel dimensions (Dialog, Panel, TextBox),
 * also pass: style={styles.dim.X}
 */

const styles = {
  // Typography
  titleText: 'tw:font-ios-display tw:font-semibold tw:text-[20px] tw:text-cpay-label tw:tracking-[0.38px]',

  // Dashboard
  dashboardChartPanel: 'tw:relative tw:float-left tw:rounded-ios-lg tw:shadow-ios-card tw:bg-cpay-bg tw:m-ios-sm',

  // Toolbar
  moduleToolBarButtons: 'tw:ml-ios-sm tw:rounded-ios-md tw:font-ios',

  // Dialogs - appearance only (dimensions via styles.dim.X)
  formDialog:                       'tw:rounded-ios-xl tw:shadow-ios-lg',
  formDialogLargeWidth:             'tw:rounded-ios-xl tw:shadow-ios-lg',
  moreTableContentDialogLargeWidth: 'tw:rounded-ios-xl tw:shadow-ios-lg',
  formDialogContainer:              'tw:px-ios-lg tw:pt-ios-lg tw:pb-ios-md tw:w-[400px] tw:font-ios',
  formDialogFields:                 'tw:p-ios-xs tw:rounded-ios-sm tw:font-ios',
  formDialogFieldsTexField:         'tw:p-ios-xs tw:rounded-ios-sm tw:font-ios',

  // Expander rows
  expanderRow:          'tw:px-ios-lg tw:pt-ios-lg tw:pb-ios-md tw:bg-cpay-bg-secondary tw:rounded-ios-md',
  expanderRowHighlight: 'tw:font-semibold tw:m-ios-xs tw:text-cpay-blue',

  // Buttons
  formDialogLargeWidthAddButtons: 'tw:my-ios-sm tw:mr-ios-sm tw:rounded-ios-pill tw:bg-cpay-blue tw:text-white',

  // Number presentations
  numberPresentationGreenBold: 'tw:text-cpay-green tw:font-semibold tw:text-[17px] tw:font-ios',
  numberPresentationGreen:     'tw:text-cpay-green tw:font-semibold tw:font-ios',
  numberPresentationRed:       'tw:text-cpay-red   tw:font-semibold tw:font-ios',

  // Code / log block
  commonBlockText: 'tw:m-ios-sm tw:overflow-x-auto tw:p-ios-md tw:bg-cpay-gray-6 tw:border tw:border-cpay-separator tw:rounded-ios-md tw:font-mono tw:text-[13px] tw:text-cpay-label tw:w-[400px]',

  // iOS card surface
  card: 'tw:bg-cpay-bg tw:rounded-ios-xl tw:shadow-ios-card tw:p-ios-lg tw:mb-ios-md',

  // Pill button
  pillButton: 'tw:bg-cpay-blue tw:text-white tw:rounded-ios-pill tw:px-ios-lg tw:py-ios-sm tw:border-0 tw:font-ios tw:font-semibold tw:text-[17px] tw:cursor-pointer',

  // Status badges
  badgeActive:   'tw:bg-cpay-green  tw:text-white tw:rounded-ios-pill tw:px-ios-sm tw:py-[2px] tw:text-[12px] tw:font-semibold tw:inline-block',
  badgeInactive: 'tw:bg-cpay-gray   tw:text-white tw:rounded-ios-pill tw:px-ios-sm tw:py-[2px] tw:text-[12px] tw:font-semibold tw:inline-block',
  badgePending:  'tw:bg-cpay-orange tw:text-white tw:rounded-ios-pill tw:px-ios-sm tw:py-[2px] tw:text-[12px] tw:font-semibold tw:inline-block',

  // ── Dimension-only objects for rc-easyui components ──────────────────────
  // rc-easyui Dialog, Panel, TextBox use style= for pixel sizing.
  // Usage: style={styles.dim.formDialog} className={styles.formDialog}
  dim: {
    formDialog:                       { width: 500,  height: 398 },
    formDialogLargeWidth:             { width: 800,  height: 398 },
    moreTableContentDialogLargeWidth: { width: 1024, height: 550 },
    dashboardChartPanel:              { height: 300, width: '45%' },
    formDialogFields:                 { width: 280 },
    formDialogFieldsTexField:         { width: 280, height: 100 },
  },
};

export default styles;
