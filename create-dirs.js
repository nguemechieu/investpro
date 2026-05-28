const fs = require('fs');
const path = require('path');

const dirs = [
  'C:\\Users\\nguem\\Documents\\GitHub\\investpro\\src\\main\\java\\org\\investpro\\activity\\persistence',
  'C:\\Users\\nguem\\Documents\\GitHub\\investpro\\src\\main\\java\\org\\investpro\\activity\\reconciliation',
  'C:\\Users\\nguem\\Documents\\GitHub\\investpro\\src\\main\\java\\org\\investpro\\activity\\readiness',
  'C:\\Users\\nguem\\Documents\\GitHub\\investpro\\src\\main\\java\\org\\investpro\\activity\\ratelimit',
  // Solana network adapter
  'C:\\Users\\nguem\\Documents\\GitHub\\investpro\\src\\main\\java\\org\\investpro\\exchange\\solana',
];

dirs.forEach(d => {
  try {
    fs.mkdirSync(d, { recursive: true });
    console.log(`✓ Created: ${d}`);
  } catch (err) {
    console.error(`✗ Failed: ${d} - ${err.message}`);
  }
});

console.log('\nVerifying directories:');
dirs.forEach(d => {
  if (fs.existsSync(d)) {
    console.log(`✓ Confirmed: ${d}`);
  } else {
    console.log(`✗ Not found: ${d}`);
  }
});
