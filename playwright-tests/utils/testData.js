export const users = {
  doctor: {
    username: process.env.DOCTOR_USERNAME,
    password: process.env.DOCTOR_PASSWORD,
    role: "DOCTOR"
  },

  nurse: {
    username: process.env.NURSE_USERNAME,
    password: process.env.NURSE_PASSWORD,
    role: "NURSE"
  },

  admin: {
    username: process.env.ADMIN_USERNAME,
    password: process.env.ADMIN_PASSWORD,
    role: "ADMIN"
  },

  superAdmin: {
    username: process.env.SUPERADMIN_USERNAME,
    password: process.env.SUPERADMIN_PASSWORD,
    role: "SUPER_ADMIN"
  }
};