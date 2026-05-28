const fs = require('fs');
const path = require('path');

const baseDir = 'C:\\Users\\nguem\\Documents\\GitHub\\investpro';
const dirs = [
  'src\\main\\java\\org\\investpro\\exchange\\execution',
  'src\\main\\java\\org\\investpro\\exchange\\routing',
  'src\\main\\java\\org\\investpro\\exchange\\throttle',
  'src\\main\\java\\org\\investpro\\exchange\\coordination',
  'src\\main\\java\\org\\investpro\\exchange\\cache',
  'src\\main\\java\\org\\investpro\\exchange\\blockchain',
  'src\\main\\java\\org\\investpro\\exchange\\distributed'
];

console.log('Creating directories...');
dirs.forEach(dir => {
  const fullPath = path.join(baseDir, dir);
  try {
    fs.mkdirSync(fullPath, { recursive: true });
    console.log(`✓ Created: ${fullPath}`);
  } catch (err) {
    console.log(`✗ Error: ${fullPath} - ${err.message}`);
  }
});

console.log('\nVerifying directories...');
dirs.forEach(dir => {
  const fullPath = path.join(baseDir, dir);
  try {
    if (fs.statSync(fullPath).isDirectory()) {
      console.log(`✓ ${fullPath}`);
    }
  } catch (err) {
    console.log(`✗ ${fullPath}`);
  }
});

console.log('\nAll directories created successfully');
