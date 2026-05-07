#!/bin/bash

# InvestPro Docker Deployment Script for Linux/macOS
# This script automates building and running InvestPro with PostgreSQL and noVNC

show_menu() {
    clear
    echo "======================================"
    echo "  InvestPro Docker Management"
    echo "======================================"
    echo ""
    echo "1. Build and Start Services"
    echo "2. Start Services (without rebuild)"
    echo "3. Stop Services"
    echo "4. View Logs"
    echo "5. View Specific Service Logs"
    echo "6. Access noVNC Web Interface"
    echo "7. Connect via VNC Client"
    echo "8. Database Shell"
    echo "9. Application Shell"
    echo "10. Restart All Services"
    echo "11. Status Check"
    echo "12. Clean Up (stop and remove containers)"
    echo "13. Exit"
    echo ""
}

build_start() {
    clear
    echo "Building and starting services..."
    docker-compose up --build -d
    if [ $? -ne 0 ]; then
        echo "Error during build/start. Check Docker installation and logs."
        read -p "Press Enter to continue..."
        return
    fi
    sleep 5
    echo "Services are starting. Checking status..."
    docker-compose ps
    echo ""
    echo "noVNC Web Interface: http://localhost:6080"
    echo "VNC Direct Connection: localhost:5900 (password: investpro)"
    echo "Database: localhost:5432"
    echo ""
    read -p "Press Enter to continue..."
}

start_services() {
    clear
    echo "Starting services..."
    docker-compose up -d
    sleep 3
    docker-compose ps
    read -p "Press Enter to continue..."
}

stop_services() {
    clear
    echo "Stopping services..."
    docker-compose stop
    echo "Services stopped."
    read -p "Press Enter to continue..."
}

view_logs() {
    clear
    echo "Showing logs (press Ctrl+C to exit)..."
    docker-compose logs -f
}

view_specific_logs() {
    clear
    echo "Which service logs?"
    echo "1. investpro-app"
    echo "2. postgres"
    echo "3. novnc"
    echo ""
    read -p "Enter choice (1-3): " service
    
    case $service in
        1)
            docker-compose logs -f investpro-app
            ;;
        2)
            docker-compose logs -f postgres
            ;;
        3)
            docker-compose logs -f novnc
            ;;
        *)
            echo "Invalid choice"
            sleep 2
            ;;
    esac
}

open_novnc() {
    clear
    echo "Opening noVNC web interface..."
    
    # Detect OS and open browser accordingly
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        xdg-open http://localhost:6080 2>/dev/null &
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        open http://localhost:6080
    else
        echo "Please manually open: http://localhost:6080"
    fi
    
    read -p "Press Enter to continue..."
}

vnc_info() {
    clear
    echo ""
    echo "To connect via VNC client:"
    echo "- Host: localhost"
    echo "- Port: 5900"
    echo "- Password: investpro"
    echo ""
    echo "Recommended VNC clients:"
    echo "- RealVNC Viewer (https://www.realvnc.com/en/connect/download/viewer/)"
    echo "- Remmina (Linux)"
    echo "- TightVNC (https://www.tightvnc.com/)"
    echo "- UltraVNC (https://www.uvnc.com/)"
    echo ""
    read -p "Press Enter to continue..."
}

db_shell() {
    clear
    echo "Connecting to PostgreSQL database..."
    docker exec -it investpro-postgres psql -U investpro -d investpro
}

app_shell() {
    clear
    echo "Entering application container..."
    docker exec -it investpro-app bash
}

restart_services() {
    clear
    echo "Restarting all services..."
    docker-compose restart
    sleep 3
    docker-compose ps
    read -p "Press Enter to continue..."
}

status_check() {
    clear
    echo "Service Status:"
    docker-compose ps
    echo ""
    echo "Checking PostgreSQL health..."
    docker exec investpro-postgres pg_isready -U investpro -d investpro
    
    echo ""
    echo "Docker disk usage:"
    docker system df
    echo ""
    read -p "Press Enter to continue..."
}

cleanup() {
    clear
    echo "WARNING: This will stop and remove all containers!"
    read -p "Continue? (yes/no): " confirm
    
    if [[ "$confirm" == "yes" ]]; then
        echo "Cleaning up..."
        docker-compose down -v
        echo "Cleanup complete. All containers and volumes removed."
    else
        echo "Cleanup cancelled."
    fi
    read -p "Press Enter to continue..."
}

# Main loop
while true; do
    show_menu
    read -p "Enter your choice (1-13): " choice
    
    case $choice in
        1)
            build_start
            ;;
        2)
            start_services
            ;;
        3)
            stop_services
            ;;
        4)
            view_logs
            ;;
        5)
            view_specific_logs
            ;;
        6)
            open_novnc
            ;;
        7)
            vnc_info
            ;;
        8)
            db_shell
            ;;
        9)
            app_shell
            ;;
        10)
            restart_services
            ;;
        11)
            status_check
            ;;
        12)
            cleanup
            ;;
        13)
            clear
            echo "Exiting..."
            exit 0
            ;;
        *)
            echo "Invalid choice. Please try again."
            sleep 2
            ;;
    esac
done
